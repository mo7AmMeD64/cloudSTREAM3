package com.kodastream.app.manga.store

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.Request
import java.io.IOException
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException

sealed interface StoreResult {
    data class Success(
        val store: ExtensionStore,
        val extensions: List<AvailableExtension>,
        val etag: String?,
        val lastModified: String?,
    ) : StoreResult

    data object NotModified : StoreResult
    data class NetworkError(val message: String) : StoreResult
    data class InvalidFormat(val message: String) : StoreResult
}

/**
 * Fetches and parses Mihon extension store indexes: gzip protobuf, current JSON, the
 * legacy `index.min.json` plus `repo.json` pair, and an `index_v2` redirect.
 */
class ExtensionStoreService(
    private val network: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    suspend fun fetch(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): StoreResult = withContext(Dispatchers.IO) {
        try {
            fetchInternal(url.trim(), etag, lastModified)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            StoreResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            StoreResult.InvalidFormat(e.message ?: "Invalid store format")
        }
    }

    private suspend fun fetchInternal(
        url: String,
        etag: String?,
        lastModified: String?,
    ): StoreResult {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "The URL must start with http:// or https://"
        }

        if (url.endsWith("repo.json")) {
            val repo = parseRepo(fetchBytes(url, null, null).bytes)
            if (repo.indexV2 != null) return fetchInternal(repo.indexV2, etag, lastModified)
            return fetchLegacy(
                indexUrl = url.removeSuffix("repo.json") + "index.min.json",
                repoUrl = url,
                repo = repo,
                etag = etag,
                lastModified = lastModified,
            )
        }

        val fetched = fetchBytes(url, etag, lastModified)
        if (fetched.notModified) return StoreResult.NotModified

        if (url.endsWith("index.min.json")) {
            val repoUrl = url.removeSuffix("index.min.json") + "repo.json"
            val repo = try {
                parseRepo(fetchBytes(repoUrl, null, null).bytes)
            } catch (e: Exception) {
                throw IllegalArgumentException("index.min.json needs a valid repo.json: ${e.message}")
            }
            if (repo.indexV2 != null) return fetchInternal(repo.indexV2, etag, lastModified)
            return fetchLegacy(url, repoUrl, repo, etag, lastModified, fetched)
        }

        val parsed = parseStore(fetched.bytes, url)
        val extensions = when {
            parsed.extensions != null -> parsed.extensions.map { it.toAvailable(parsed.store) }
            parsed.extensionListUrl != null -> {
                val listBytes = fetchBytes(parsed.extensionListUrl, null, null).bytes
                parseExtensionList(listBytes).map { it.toAvailable(parsed.store) }
            }
            else -> emptyList()
        }
        return StoreResult.Success(parsed.store, extensions, fetched.etag, fetched.lastModified)
    }

    private suspend fun fetchLegacy(
        indexUrl: String,
        repoUrl: String,
        repo: LegacyRepo,
        etag: String?,
        lastModified: String?,
        preFetched: Fetched? = null,
    ): StoreResult {
        val fetched = preFetched ?: fetchBytes(indexUrl, etag, lastModified)
        if (fetched.notModified) return StoreResult.NotModified
        val store = repo.toStore(repoUrl)
        val extensions = json.decodeFromString<List<LegacyExtension>>(fetched.bytes.decodeToString())
            .map { it.toAvailable(store, repoUrl.removeSuffix("repo.json")) }
        return StoreResult.Success(store, extensions, fetched.etag, fetched.lastModified)
    }

    private fun parseStore(bytes: ByteArray, url: String): ParsedStore {
        return when (sniff(bytes)) {
            Format.JSON -> {
                val source = json.decodeFromString<JsonStore>(bytes.decodeToString())
                ParsedStore(
                    store = ExtensionStore(
                        indexUrl = url,
                        name = source.name.ifBlank { source.badgeLabel.ifBlank { "Extension store" } },
                        badgeLabel = source.badgeLabel.ifBlank { source.name },
                        signingKey = source.signingKey,
                        website = source.contact?.website?.takeIf { it.isNotBlank() },
                        discord = source.contact?.discord?.takeIf { it.isNotBlank() },
                    ),
                    extensions = source.extensionList?.extensions?.map { it.toProto() },
                    extensionListUrl = source.extensionListUrl,
                )
            }
            Format.PROTOBUF -> {
                val source = protoBuf.decodeFromByteArray(ProtoStore.serializer(), bytes)
                ParsedStore(
                    store = ExtensionStore(
                        indexUrl = url,
                        name = source.name.ifBlank { source.badgeLabel.ifBlank { "Extension store" } },
                        badgeLabel = source.badgeLabel.ifBlank { source.name },
                        signingKey = source.signingKey,
                        website = source.contact?.website?.takeIf { it.isNotBlank() },
                        discord = source.contact?.discord?.takeIf { it.isNotBlank() },
                    ),
                    extensions = source.extensionList?.extensions,
                    extensionListUrl = source.extensionListUrl,
                )
            }
            Format.UNKNOWN -> throw IllegalArgumentException("Unsupported store format at $url")
        }
    }

    private fun parseExtensionList(bytes: ByteArray): List<ProtoExtension> {
        return when (sniff(bytes)) {
            Format.JSON -> json.decodeFromString<JsonExtensionList>(bytes.decodeToString())
                .extensions
                .map { it.toProto() }
            else -> protoBuf.decodeFromByteArray(ProtoExtensionList.serializer(), bytes).extensions
        }
    }

    private fun parseRepo(bytes: ByteArray): LegacyRepo =
        json.decodeFromString<LegacyRepo>(bytes.decodeToString())

    private data class ParsedStore(
        val store: ExtensionStore,
        val extensions: List<ProtoExtension>?,
        val extensionListUrl: String?,
    )

    private fun JsonExtension.toProto() = ProtoExtension(
        name = name,
        packageName = packageName,
        resources = ProtoResources(resources.apkUrl, resources.iconUrl),
        extensionLib = extensionLib,
        versionCode = versionCode,
        versionName = versionName,
        contentWarning = contentWarning,
        sources = sources.map { source ->
            ProtoSource(
                id = source.id,
                name = source.name,
                language = source.language,
                homeUrl = source.homeUrl,
                mirrorUrls = source.mirrorUrls,
                message = source.message,
            )
        },
    )

    private fun LegacyRepo.toStore(repoUrl: String) = ExtensionStore(
        indexUrl = repoUrl,
        name = meta.name,
        badgeLabel = meta.shortName ?: meta.name,
        signingKey = meta.signingKeyFingerprint,
        website = meta.website.takeIf { it.isNotBlank() },
        isLegacy = true,
    )

    private suspend fun fetchBytes(
        url: String,
        etag: String?,
        lastModified: String?,
    ): Fetched {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", network.defaultUserAgentProvider())
            .apply {
                if (etag != null) header("If-None-Match", etag)
                if (lastModified != null) header("If-Modified-Since", lastModified)
            }
            .build()
        return network.client.newCall(request).await().use { response ->
            if (response.code == 304) {
                return Fetched(byteArrayOf(), true, etag, lastModified)
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("Empty response for $url")
            val raw = body.bytes()
            val bytes = if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                GZIPInputStream(raw.inputStream()).use { it.readBytes() }
            } else {
                raw
            }
            Fetched(bytes, false, response.header("ETag"), response.header("Last-Modified"))
        }
    }

    private fun sniff(bytes: ByteArray): Format {
        val first = bytes.firstOrNull { it != 0x20.toByte() && it != 0x09.toByte() && it != 0x0A.toByte() && it != 0x0D.toByte() }
            ?: return Format.UNKNOWN
        return when (first) {
            '['.code.toByte(), '{'.code.toByte() -> Format.JSON
            else -> Format.PROTOBUF
        }
    }

    private enum class Format { JSON, PROTOBUF, UNKNOWN }

    private data class Fetched(
        val bytes: ByteArray,
        val notModified: Boolean,
        val etag: String?,
        val lastModified: String?,
    )
}
