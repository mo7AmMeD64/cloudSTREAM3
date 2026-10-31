@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.kodastream.app.manga.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

enum class ContentWarning { SAFE, MIXED, NSFW }

@Serializable
data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val website: String? = null,
    val discord: String? = null,
    val isLegacy: Boolean = false,
)

@Serializable
data class ExtensionSource(
    val id: Long,
    val name: String,
    val language: String,
    val baseUrl: String,
    val mirrorUrls: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class AvailableExtension(
    val name: String,
    val pkgName: String,
    val apkUrl: String,
    val iconUrl: String,
    val versionCode: Long,
    val versionName: String,
    val libVersion: Double?,
    val lang: String,
    val contentWarning: ContentWarning,
    val sources: List<ExtensionSource>,
    val storeUrl: String,
    val storeName: String,
    val badgeLabel: String,
    val storeSigningKey: String = "",
)

@Serializable
data class StoreCacheEntry(
    val storeUrl: String,
    val etag: String? = null,
    val lastModified: String? = null,
    val fetchedAt: Long = 0L,
    val extensions: List<AvailableExtension> = emptyList(),
)

// ---- Wire models of the store index (Mihon's field numbers, Apache-2.0) ----

@Serializable
internal data class ProtoStore(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: ProtoContact? = null,
    @ProtoNumber(101) val extensionList: ProtoExtensionList? = null,
    @ProtoNumber(102) val extensionListUrl: String? = null,
)

@Serializable
internal data class ProtoContact(
    @ProtoNumber(1) val website: String = "",
    @ProtoNumber(2) val discord: String = "",
)

@Serializable
internal data class ProtoExtensionList(
    @ProtoNumber(1) val extensions: List<ProtoExtension> = emptyList(),
)

@Serializable
internal data class ProtoExtension(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val packageName: String = "",
    @ProtoNumber(3) val resources: ProtoResources = ProtoResources(),
    @ProtoNumber(4) val extensionLib: String = "",
    @ProtoNumber(5) val versionCode: Long = 0,
    @ProtoNumber(6) val versionName: String = "",
    @ProtoNumber(7) val contentWarning: Int = 0,
    @ProtoNumber(8) val sources: List<ProtoSource> = emptyList(),
)

@Serializable
internal data class ProtoResources(
    @ProtoNumber(1) val apkUrl: String = "",
    @ProtoNumber(2) val iconUrl: String = "",
    @ProtoNumber(3) val jarUrl: String = "",
)

@Serializable
internal data class ProtoSource(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val language: String = "",
    @ProtoNumber(4) val homeUrl: String = "",
    @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
    @ProtoNumber(7) val message: String? = null,
)

@Serializable
internal data class JsonStore(
    val name: String = "",
    val badgeLabel: String = "",
    val signingKey: String = "",
    val contact: JsonContact? = null,
    val extensionList: JsonExtensionList? = null,
    val extensionListUrl: String? = null,
)

@Serializable
internal data class JsonContact(val website: String = "", val discord: String = "")

@Serializable
internal data class JsonExtensionList(val extensions: List<JsonExtension> = emptyList())

@Serializable
internal data class JsonExtension(
    val name: String = "",
    val packageName: String = "",
    val resources: JsonResources = JsonResources(),
    val extensionLib: String = "",
    val versionCode: Long = 0,
    val versionName: String = "",
    val contentWarning: Int = 0,
    val sources: List<JsonSource> = emptyList(),
)

@Serializable
internal data class JsonResources(val apkUrl: String = "", val iconUrl: String = "")

@Serializable
internal data class JsonSource(
    val id: Long = 0,
    val name: String = "",
    val language: String = "",
    val homeUrl: String = "",
    val mirrorUrls: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
internal data class LegacyRepo(
    @SerialName("index_v2") val indexV2: String? = null,
    val meta: LegacyMeta,
)

@Serializable
internal data class LegacyMeta(
    val name: String,
    val shortName: String? = null,
    val website: String = "",
    val signingKeyFingerprint: String,
)

@Serializable
internal data class LegacyExtension(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<LegacySource>? = null,
)

@Serializable
internal data class LegacySource(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

internal fun ProtoExtension.toAvailable(
    store: ExtensionStore,
): AvailableExtension {
    val languages = sources.map { it.language }.toSet()
    return AvailableExtension(
        name = name.substringAfter("Tachiyomi: "),
        pkgName = packageName,
        apkUrl = resources.apkUrl,
        iconUrl = resources.iconUrl,
        versionCode = versionCode,
        versionName = versionName,
        libVersion = extensionLib.toDoubleOrNull(),
        lang = if (languages.size == 1) languages.first() else "all",
        contentWarning = when (contentWarning) {
            2 -> ContentWarning.MIXED
            3 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        },
        sources = sources.map {
            ExtensionSource(
                id = it.id,
                name = it.name,
                language = it.language,
                baseUrl = it.homeUrl,
                mirrorUrls = it.mirrorUrls,
                message = it.message,
            )
        },
        storeUrl = store.indexUrl,
        storeName = store.name,
        badgeLabel = store.badgeLabel,
        storeSigningKey = store.signingKey,
    )
}

internal fun JsonExtension.toAvailable(
    store: ExtensionStore,
): AvailableExtension {
    val languages = sources.map { it.language }.toSet()
    return AvailableExtension(
        name = name.substringAfter("Tachiyomi: "),
        pkgName = packageName,
        apkUrl = resources.apkUrl,
        iconUrl = resources.iconUrl,
        versionCode = versionCode,
        versionName = versionName,
        libVersion = extensionLib.toDoubleOrNull(),
        lang = if (languages.size == 1) languages.first() else "all",
        contentWarning = when (contentWarning) {
            2 -> ContentWarning.MIXED
            3 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        },
        sources = sources.map {
            ExtensionSource(
                id = it.id,
                name = it.name,
                language = it.language,
                baseUrl = it.homeUrl,
                mirrorUrls = it.mirrorUrls,
                message = it.message,
            )
        },
        storeUrl = store.indexUrl,
        storeName = store.name,
        badgeLabel = store.badgeLabel,
        storeSigningKey = store.signingKey,
    )
}

internal fun LegacyExtension.toAvailable(
    store: ExtensionStore,
    storeBaseUrl: String,
): AvailableExtension = AvailableExtension(
    name = name.substringAfter("Tachiyomi: "),
    pkgName = pkg,
    apkUrl = "$storeBaseUrl/apk/$apk",
    iconUrl = "$storeBaseUrl/icon/$pkg.png",
    versionCode = code,
    versionName = version,
    libVersion = version.substringBeforeLast('.').toDoubleOrNull(),
    lang = lang,
    contentWarning = if (nsfw == 1) ContentWarning.NSFW else ContentWarning.SAFE,
    sources = sources?.map {
        ExtensionSource(
            id = it.id,
            name = it.name,
            language = it.lang,
            baseUrl = it.baseUrl,
        )
    } ?: listOf(ExtensionSource(0, name, lang, "")),
    storeUrl = store.indexUrl,
    storeName = store.name,
    badgeLabel = store.badgeLabel,
    storeSigningKey = store.signingKey,
)
