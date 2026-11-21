package com.kodastream.app.ui.manga

import com.kodastream.app.manga.MangaChapter
import com.kodastream.app.manga.MangaEntry
import com.kodastream.app.manga.download.downloadKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaChapterBatchTest {
    private val entry = MangaEntry(sourceId = 7L, url = "manga")

    private fun chapter(number: Int) = MangaChapter(
        url = "chapter-$number",
        name = "Chapter $number",
        sourceOrder = number,
    )

    @Test
    fun keepsTheShownOrder() {
        val shown = listOf(chapter(3), chapter(1), chapter(2))

        val batch = nextDownloadBatch(shown, entry, emptySet(), emptySet())

        assertEquals(listOf("chapter-3", "chapter-1", "chapter-2"), batch.map { it.url })
    }

    @Test
    fun skipsDownloadedAndQueuedChapters() {
        val shown = (1..5).map(::chapter)

        val batch = nextDownloadBatch(
            shown = shown,
            entry = entry,
            downloaded = setOf("chapter-1", "chapter-2"),
            queuedKeys = setOf(downloadKey(entry, chapter(3))),
        )

        assertEquals(listOf("chapter-4", "chapter-5"), batch.map { it.url })
    }

    @Test
    fun nextBatchContinuesAfterTheFirstHundred() {
        val shown = (1..250).map(::chapter)
        val first = nextDownloadBatch(shown, entry, emptySet(), emptySet()).take(100)
        val downloaded = first.mapTo(HashSet()) { it.url }

        val second = nextDownloadBatch(shown, entry, downloaded, emptySet()).take(100)

        assertEquals("chapter-101", second.first().url)
        assertEquals(100, second.size)
    }
}
