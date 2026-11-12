package com.kodastream.app.manga.extension

import com.kodastream.app.manga.isNewerLib
import com.kodastream.app.manga.signatureMatches
import com.kodastream.app.manga.store.ContentWarning
import com.kodastream.app.manga.store.ExtensionStore
import eu.kanade.tachiyomi.source.Source

sealed interface MangaExtension {
    val name: String
    val pkgName: String
    val versionName: String
    val versionCode: Long
    val libVersion: Double?
    val lang: String?
    val contentWarning: ContentWarning
    val store: ExtensionStore?
    val signatures: List<String>
    val isShared: Boolean

    sealed interface Installed : MangaExtension {
        fun findUpdate(available: List<com.kodastream.app.manga.store.AvailableExtension>): com.kodastream.app.manga.store.AvailableExtension?
    }

    data class Loaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double?,
        override val lang: String?,
        override val contentWarning: ContentWarning,
        override val store: ExtensionStore?,
        override val signatures: List<String>,
        override val isShared: Boolean,
        val sources: List<Source>,
        val factoryClassName: String?,
    ) : Installed {

        val isConfigurable: Boolean
            get() = sources.any { it is eu.kanade.tachiyomi.source.ConfigurableSource }

        override fun findUpdate(
            available: List<com.kodastream.app.manga.store.AvailableExtension>,
        ): com.kodastream.app.manga.store.AvailableExtension? {
            return available
                .filter { it.pkgName == pkgName && signatureMatches(signatures, it.storeSigningKey) }
                .maxWithOrNull(
                    compareBy<com.kodastream.app.manga.store.AvailableExtension> { it.versionCode }
                        .thenBy { it.libVersion ?: 0.0 },
                )
                ?.takeIf {
                    it.versionCode > versionCode || (libVersion != null && isNewerLib(it.libVersion, libVersion))
                }
        }
    }

    data class NotLoaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double?,
        override val lang: String?,
        override val contentWarning: ContentWarning,
        override val store: ExtensionStore?,
        override val signatures: List<String>,
        override val isShared: Boolean,
        val reason: Reason,
    ) : Installed {
        override fun findUpdate(
            available: List<com.kodastream.app.manga.store.AvailableExtension>,
        ): com.kodastream.app.manga.store.AvailableExtension? = null

        sealed interface Reason {
            data object Malformed : Reason
            data object UnsupportedLibVersion : Reason
            data object Unsigned : Reason
            data class Untrusted(val signature: String) : Reason
            data object Filtered : Reason
            data class Failed(val message: String, val stackTrace: String) : Reason
        }
    }
}

data class LoadedSource(
    val source: Source,
    val extension: MangaExtension.Loaded,
    val language: String,
)
