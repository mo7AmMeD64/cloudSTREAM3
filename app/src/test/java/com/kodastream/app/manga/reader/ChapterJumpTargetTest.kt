package com.kodastream.app.manga.reader

import com.kodastream.app.manga.MangaChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterJumpTargetTest {

    private fun chapterWithPages(url: String, number: Float, pageCount: Int = 3): ReaderChapter =
        ReaderChapter(
            MangaChapter(
                url = url,
                name = "Chapter $url",
                number = number,
                sourceOrder = number.toInt(),
            ),
        ).also { chapter ->
            chapter.state = ReaderChapter.State.Loaded(
                List(pageCount) { index ->
                    ReaderPage(index, "p$index-$url").also { it.chapter = chapter }
                },
            )
        }

    @Test
    fun `switching to the next chapter moves to its requested page`() {
        val previous = chapterWithPages("1", 1f)
        val next = chapterWithPages("2", 2f).also { it.requestedPage = 1 }

        val target = chapterJumpTarget(previous.pages?.first(), ViewerChapters(next, previous, null))

        assertEquals(next.pages?.get(1), target)
    }

    @Test
    fun `scrolling into a chapter does not jump back`() {
        val chapter = chapterWithPages("2", 2f).also { it.requestedPage = 0 }

        val target = chapterJumpTarget(chapter.pages?.get(1), ViewerChapters(chapter, null, null))

        assertNull(target)
    }

    @Test
    fun `first emission jumps to the requested page`() {
        val chapter = chapterWithPages("3", 3f).also { it.requestedPage = 2 }

        val target = chapterJumpTarget(null, ViewerChapters(chapter, null, null))

        assertEquals(chapter.pages?.get(2), target)
    }
}
