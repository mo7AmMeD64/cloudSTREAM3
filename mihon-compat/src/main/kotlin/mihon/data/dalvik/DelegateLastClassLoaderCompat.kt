package mihon.data.dalvik

import android.os.Build
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import java.net.URL
import java.util.Collections
import java.util.Enumeration

// Copied from Mihon (Apache-2.0). Cinnamon Bun is API 36; compare numerically so the
// project's compileSdk doesn't need the constant.
private const val CINNAMON_BUN = 36

@Suppress("FunctionName")
fun DelegateLastClassLoaderCompat(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
): ClassLoader {
    return if (Build.VERSION.SDK_INT >= CINNAMON_BUN) {
        DelegateLastClassLoader(dexPath, librarySearchPath, parent)
    } else {
        DelegateLastPathClassLoader(dexPath, librarySearchPath, parent)
    }
}

private class DelegateLastPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    private val bootClassLoader: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }

        if (bootClassLoader != null) {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {}
        }

        val fromSuper = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }

        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw fromSuper
        }
    }

    override fun getResource(name: String?): URL? {
        return bootClassLoader?.getResource(name)
            ?: findResource(name)
            ?: parent?.getResource(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        val resources = buildList {
            bootClassLoader?.getResources(name)?.let { addAll(it.toList()) }
            findResources(name)?.let { addAll(it.toList()) }
            parent?.getResources(name)?.let { addAll(it.toList()) }
        }
        return Collections.enumeration(resources)
    }
}
