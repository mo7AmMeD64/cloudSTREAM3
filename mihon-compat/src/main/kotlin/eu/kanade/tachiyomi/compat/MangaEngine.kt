package eu.kanade.tachiyomi.compat

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type

/**
 * Lazily built holder for everything the Mihon extension runtime needs. Nothing here is
 * touched at app start; the manga section calls [get] on first use.
 */
object MangaEngine {
    @Volatile
    private var state: State? = null

    fun get(context: Context): State {
        state?.let { return it }
        synchronized(this) {
            state?.let { return it }
            val built = try {
                create(context.applicationContext)
            } catch (e: Throwable) {
                State(null, null, Json { ignoreUnknownKeys = true }, ProtoBuf, e.stackTraceToString())
            }
            state = built
            return built
        }
    }

    private fun create(context: Context): State {
        val json = Json { ignoreUnknownKeys = true }
        val protoBuf = ProtoBuf
        lateinit var network: NetworkHelper
        lateinit var javaScriptEngine: JavaScriptEngine
        network = NetworkHelper(context, NetworkPreferences(context))
        javaScriptEngine = JavaScriptEngine(context)

        Injekt = InjektScope(
            Registrar(
                application = context as Application,
                json = json,
                protoBuf = protoBuf,
                network = network,
                javaScriptEngine = javaScriptEngine,
            ),
        )
        return State(network, javaScriptEngine, json, protoBuf, null)
    }

    data class State(
        val network: NetworkHelper?,
        val javaScriptEngine: JavaScriptEngine?,
        val json: Json,
        val protoBuf: ProtoBuf,
        val error: String?,
    ) {
        val isReady: Boolean get() = error == null && network != null
    }

    private class Registrar(
        private val application: Application,
        private val json: Json,
        private val protoBuf: ProtoBuf,
        private val network: NetworkHelper,
        private val javaScriptEngine: JavaScriptEngine,
    ) : InjektRegistrar {
        private val bindings = mapOf<Type, () -> Any>(
            Application::class.java to { application },
            Context::class.java to { application },
            Json::class.java to { json },
            ProtoBuf::class.java to { protoBuf },
            NetworkHelper::class.java to { network },
            JavaScriptEngine::class.java to { javaScriptEngine },
        )

        override fun <R : Any> getInstance(forType: Type): R = getInstanceOrNull(forType)
            ?: throw InjektionException("$forType is not exposed to Injekt")

        override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R =
            getInstanceOrNull(forType) ?: default

        override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
            getInstanceOrNull(forType) ?: default()

        @Suppress("UNCHECKED_CAST")
        override fun <R : Any> getInstanceOrNull(forType: Type): R? = bindings[forType]?.invoke() as R?

        override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R =
            unsupported("Keyed injection")

        override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R =
            unsupported("Keyed injection")

        override fun <R : Any, K : Any> getKeyedInstanceOrElse(
            forType: Type,
            key: K,
            default: () -> R,
        ): R = unsupported("Keyed injection")

        override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? =
            unsupported("Keyed injection")

        override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R =
            unsupported("Logger injection")

        override fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R =
            unsupported("Logger injection")

        override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) = readOnly()

        override fun <R : Any> addSingletonFactory(
            forType: TypeReference<R>,
            factoryCalledOnce: () -> R,
        ) = readOnly()

        override fun <R : Any> addFactory(
            forType: TypeReference<R>,
            factoryCalledEveryTime: () -> R,
        ) = readOnly()

        override fun <R : Any> addPerThreadFactory(
            forType: TypeReference<R>,
            factoryCalledOncePerThread: () -> R,
        ) = readOnly()

        override fun <R : Any, K : Any> addPerKeyFactory(
            forType: TypeReference<R>,
            factoryCalledPerKey: (K) -> R,
        ) = readOnly()

        override fun <R : Any, K : Any> addPerThreadPerKeyFactory(
            forType: TypeReference<R>,
            factoryCalledPerKeyPerThread: (K) -> R,
        ) = readOnly()

        override fun <R : Any> addLoggerFactory(
            forLoggerType: TypeReference<R>,
            factoryByName: (String) -> R,
            factoryByClass: (Class<Any>) -> R,
        ) = readOnly()

        override fun <O : Any, T : O> addAlias(
            existingRegisteredType: TypeReference<T>,
            otherAncestorOrInterface: TypeReference<O>,
        ) = readOnly()

        override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean =
            forType.type in bindings

        override fun importModule(submodule: InjektModule) = readOnly()

        private fun readOnly(): Nothing =
            throw UnsupportedOperationException("Mihon's Injekt instance is read-only")

        private fun unsupported(feature: String): Nothing =
            throw UnsupportedOperationException("$feature is not supported by Mihon's Injekt instance")
    }
}
