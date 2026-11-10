package com.kodastream.app.manga.extension

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLinkProbeTest {
    @Test
    fun noClassDefFoundErrorIsIncompatible() {
        val failure = MangaLinkProbe.check {
            throw NoClassDefFoundError("okhttp3/brotli/Brotli")
        }

        assertNotNull(failure)
        assertTrue(failure!!.message.contains("okhttp3/brotli/Brotli"))
    }

    @Test
    fun noSuchMethodErrorIsIncompatible() {
        assertNotNull(MangaLinkProbe.check { throw NoSuchMethodError("missing") })
    }

    @Test
    fun incompatibleClassChangeErrorIsIncompatible() {
        assertNotNull(MangaLinkProbe.check { throw IncompatibleClassChangeError("changed") })
    }

    @Test
    fun classNotFoundIsIncompatible() {
        assertNotNull(MangaLinkProbe.check { throw ClassNotFoundException("missing") })
    }

    @Test
    fun ordinaryExceptionsAreNotLinkFailures() {
        assertNull(MangaLinkProbe.check { throw IllegalStateException("offline") })
    }

    @Test
    fun fakeSourceWithBrokenClientIsIncompatible() {
        val failure = MangaLinkProbe.probe(
            ThrowingSource(NoClassDefFoundError("okhttp3/brotli/Brotli")),
        )

        assertNotNull(failure)
    }

    @Test
    fun fakeSourceWithOrdinaryFailureStaysLinkable() {
        assertNull(MangaLinkProbe.probe(ThrowingSource(IllegalStateException("offline"))))
    }

    private class ThrowingSource(private val error: Throwable) : HttpSource() {
        override val baseUrl: String = "https://example.com"
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override val client: OkHttpClient get() = throw error

        override suspend fun getPopularManga(page: Int): MangasPage =
            throw UnsupportedOperationException()

        override suspend fun getLatestUpdates(page: Int): MangasPage =
            throw UnsupportedOperationException()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = throw UnsupportedOperationException()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            throw UnsupportedOperationException()
    }
}
