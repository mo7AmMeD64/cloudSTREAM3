package com.kodastream.app.manga.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class MangaStoreParsingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val protoBuf = ProtoBuf

    @Test
    fun handEncodedProtobufStoreDecodes() {
        val source = concat(
            intField(1, 42),
            stringField(2, "MangaDex"),
            stringField(3, "en"),
            stringField(4, "https://mangadex.org"),
            stringField(5, "https://mirror.example"),
            stringField(7, "Maintenance tonight"),
        )
        val sourceAr = concat(
            intField(1, 43),
            stringField(2, "MangaDex AR"),
            stringField(3, "ar"),
        )
        val extension = concat(
            stringField(1, "Tachiyomi: MangaDex"),
            stringField(2, "eu.kanade.tachiyomi.extension.all.mangadex"),
            bytesField(
                3,
                concat(
                    stringField(1, "https://example.test/mangadex.apk"),
                    stringField(2, "https://example.test/mangadex.png"),
                ),
            ),
            stringField(4, "1.5"),
            longField(5, 42),
            stringField(6, "1.5.2"),
            intField(7, 2),
            bytesField(8, source),
            bytesField(8, sourceAr),
        )
        val secondExtension = concat(
            stringField(1, "Tachiyomi: Lezhin"),
            stringField(2, "eu.kanade.tachiyomi.extension.en.lezhin"),
            bytesField(3, stringField(1, "https://example.test/lezhin.apk")),
            stringField(4, "1.4"),
            longField(5, 7),
            stringField(6, "1.4.0"),
            intField(7, 1),
            bytesField(8, stringField(3, "en")),
        )
        val storeBytes = concat(
            stringField(1, "Keiyoushi"),
            stringField(2, "Keiyoushi"),
            stringField(3, "0123456789abcdef"),
            bytesField(101, concat(bytesField(1, extension), bytesField(1, secondExtension))),
        )

        val parsed = protoBuf.decodeFromByteArray(ProtoStore.serializer(), storeBytes)
        val store = ExtensionStore(
            indexUrl = "https://example.test/index.pb",
            name = parsed.name,
            badgeLabel = parsed.badgeLabel,
            signingKey = parsed.signingKey,
        )
        val available = parsed.extensionList!!.extensions[0].toAvailable(store)
        val second = parsed.extensionList!!.extensions[1].toAvailable(store)

        assertEquals("Keiyoushi", store.name)
        assertEquals("0123456789abcdef", store.signingKey)
        assertEquals("MangaDex", available.name)
        assertEquals("eu.kanade.tachiyomi.extension.all.mangadex", available.pkgName)
        assertEquals("https://example.test/mangadex.apk", available.apkUrl)
        assertEquals(42L, available.versionCode)
        assertEquals(1.5, available.libVersion!!, 0.0)
        assertEquals("all", available.lang)
        assertEquals(2, available.sources.size)
        assertEquals(42L, available.sources[0].id)
        assertEquals("https://mirror.example", available.sources[0].mirrorUrls.single())
        assertEquals("Maintenance tonight", available.sources[0].message)
        assertEquals(43L, available.sources[1].id)
        assertEquals("Lezhin", second.name)
        assertEquals("eu.kanade.tachiyomi.extension.en.lezhin", second.pkgName)
        assertEquals("https://example.test/lezhin.apk", second.apkUrl)
        assertEquals(7L, second.versionCode)
        assertEquals("1.4.0", second.versionName)
        assertEquals("en", second.lang)
        assertEquals(ContentWarning.SAFE, second.contentWarning)
        assertEquals("en", second.sources.single().language)
    }

    @Test
    fun languageGroupsBySourceLanguages() {
        val store = ExtensionStore("store", "Store", "Store", "key")
        val oneLanguage = ProtoExtension(
            name = "One",
            sources = listOf(ProtoSource(language = "ar")),
        ).toAvailable(store)
        val manyLanguages = ProtoExtension(
            name = "Many",
            sources = listOf(ProtoSource(language = "en"), ProtoSource(language = "ar")),
        ).toAvailable(store)
        val noSources = ProtoExtension(name = "None").toAvailable(store)

        assertEquals("ar", oneLanguage.lang)
        assertEquals("all", manyLanguages.lang)
        assertEquals("all", noSources.lang)
    }

    @Test
    fun contentWarningNumbersMapToTheirLabels() {
        val store = ExtensionStore("store", "Store", "Store", "key")
        val source = listOf(ProtoSource(language = "en"))

        assertEquals(ContentWarning.SAFE, ProtoExtension(sources = source).toAvailable(store).contentWarning)
        assertEquals(ContentWarning.SAFE, ProtoExtension(contentWarning = 1, sources = source).toAvailable(store).contentWarning)
        assertEquals(ContentWarning.MIXED, ProtoExtension(contentWarning = 2, sources = source).toAvailable(store).contentWarning)
        assertEquals(ContentWarning.NSFW, ProtoExtension(contentWarning = 3, sources = source).toAvailable(store).contentWarning)
    }

    @Test
    fun gzipIndexesAreUnpackedBeforeSniffing() {
        val payload = """ { "name": "Compressed" } """.toByteArray()
        val gzipped = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(payload) }
        }.toByteArray()

        assertArrayEquals(payload, gunzipIfNeeded(gzipped))
        assertEquals(StoreFormat.JSON, sniffStoreFormat(gunzipIfNeeded(gzipped)))
    }

    @Test
    fun sniffSkipsWhitespaceAndRejectsEmptyPayloads() {
        assertEquals(StoreFormat.JSON, sniffStoreFormat("\n\t [1]".toByteArray()))
        assertEquals(StoreFormat.PROTOBUF, sniffStoreFormat(byteArrayOf(0x0A, 0x03)))
        assertEquals(StoreFormat.UNKNOWN, sniffStoreFormat(" \n\t".toByteArray()))
    }

    @Test
    fun legacyRepoReadsWithAndWithoutIndexV2() {
        val withV2 = json.decodeFromString<LegacyRepo>(
            """
            {
              "index_v2": "https://example.test/index.pb",
              "meta": {
                "name": "Legacy",
                "shortName": "L",
                "website": "https://example.test",
                "signingKeyFingerprint": "fingerprint"
              }
            }
            """.trimIndent()
        )
        val withoutV2 = json.decodeFromString<LegacyRepo>(
            """
            {
              "meta": {
                "name": "Legacy",
                "signingKeyFingerprint": "fingerprint"
              }
            }
            """.trimIndent()
        )

        assertEquals("https://example.test/index.pb", withV2.indexV2)
        assertEquals("L", withV2.meta.shortName)
        assertNull(withoutV2.indexV2)
        assertEquals("fingerprint", withoutV2.meta.signingKeyFingerprint)
    }

    @Test
    fun filtersKeepMultiLanguageEntriesAndHideOnlyNsfw() {
        val all = listOf(
            available("en", ContentWarning.SAFE),
            available("all", ContentWarning.MIXED),
            available("ar", ContentWarning.SAFE),
            available("en", ContentWarning.NSFW),
        )

        val languages = MangaFilters.filterLanguages(all, setOf("en"), allLanguages = false)
        assertEquals(listOf("en", "all", "en"), languages.map { it.lang })

        val safe = MangaFilters.filterContentWarning(all)
        assertTrue(safe.none { it.contentWarning == ContentWarning.NSFW })
        assertTrue(safe.any { it.contentWarning == ContentWarning.MIXED })
        assertEquals(all, MangaFilters.filterLanguages(all, emptySet(), allLanguages = false))
    }

    @Test
    fun searchMatchesNamesPackagesAndSources() {
        val mangaDex = available("en", ContentWarning.SAFE).copy(
            name = "MangaDex",
            pkgName = "eu.kanade.tachiyomi.extension.all.mangadex",
            sources = listOf(ExtensionSource(1, "MangaDex", "en", "https://mangadex.org")),
        )

        assertEquals(listOf(mangaDex), MangaFilters.search(listOf(mangaDex), "dex"))
        assertEquals(listOf(mangaDex), MangaFilters.search(listOf(mangaDex), "tachiyomi"))
        assertEquals(listOf(mangaDex), MangaFilters.search(listOf(mangaDex), "mangadex"))
        assertTrue(MangaFilters.search(listOf(mangaDex), "   ").isNotEmpty())
    }

    @Test
    fun updateListingMustComeFromTheInstalledSignature() {
        val signed = available("en", ContentWarning.SAFE).copy(
            versionCode = 10,
            storeSigningKey = "signature-a",
        )
        val unsignedNewer = available("en", ContentWarning.SAFE).copy(
            versionCode = 99,
            storeSigningKey = "signature-b",
        )

        assertEquals(
            signed,
            MangaFilters.findListing("pkg", listOf(unsignedNewer, signed), listOf("signature-a")),
        )
        assertNull(MangaFilters.findListing("pkg", listOf(unsignedNewer), listOf("signature-a")))
        assertNull(MangaFilters.findListing("pkg", listOf(signed), emptyList()))
    }

    @Test
    fun updateCoversVersionCodeAndLibraryVersion() {
        val listing = available("en", ContentWarning.SAFE).copy(
            versionCode = 5,
            libVersion = 1.6,
        )

        assertTrue(MangaFilters.hasUpdate(4, 1.6, listing))
        assertTrue(MangaFilters.hasUpdate(5, 1.5, listing))
        assertTrue(!MangaFilters.hasUpdate(5, 1.6, listing))
        assertTrue(!MangaFilters.hasUpdate(4, 1.6, null))
    }

    private fun available(language: String, warning: ContentWarning) = AvailableExtension(
        name = "Extension",
        pkgName = "pkg",
        apkUrl = "https://example.test/ext.apk",
        iconUrl = "https://example.test/ext.png",
        versionCode = 1,
        versionName = "1.0",
        libVersion = 1.5,
        lang = language,
        contentWarning = warning,
        sources = emptyList(),
        storeUrl = "https://example.test/index.pb",
        storeName = "Store",
        badgeLabel = "Store",
    )

    private fun concat(vararg parts: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output -> parts.forEach(output::write) }.toByteArray()

    private fun bytesField(field: Int, value: ByteArray): ByteArray =
        concat(tag(field, 2), varint(value.size.toLong()), value)

    private fun stringField(field: Int, value: String): ByteArray =
        bytesField(field, value.toByteArray())

    private fun intField(field: Int, value: Int): ByteArray =
        concat(tag(field, 0), varint(value.toLong()))

    private fun longField(field: Int, value: Long): ByteArray =
        concat(tag(field, 0), varint(value))

    private fun tag(field: Int, wireType: Int): ByteArray =
        varint(((field shl 3) or wireType).toLong())

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val output = ByteArrayOutputStream()
        do {
            var byte = (remaining and 0x7f).toInt()
            remaining = remaining ushr 7
            if (remaining != 0L) byte = byte or 0x80
            output.write(byte)
        } while (remaining != 0L)
        return output.toByteArray()
    }
}
