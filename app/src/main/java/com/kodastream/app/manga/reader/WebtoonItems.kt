package com.kodastream.app.manga.reader

/**
 * Ported from Mihon's `viewer/webtoon/WebtoonItems.kt`; holds the current
 * chapter and the computed items of the webtoon list. It exists so the
 * tracker can follow pages further than one chapter boundary.
 */
class WebtoonItems {

    var items: List<Any> = emptyList()
        private set

    var currentChapter: ReaderChapter? = null
        private set

    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        currentChapter = chapters.currChapter
        items = viewerItems(chapters, forceTransition)
    }
}
