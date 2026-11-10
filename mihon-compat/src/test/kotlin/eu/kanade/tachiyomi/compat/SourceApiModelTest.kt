package eu.kanade.tachiyomi.compat

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapterImpl
import eu.kanade.tachiyomi.source.model.SMangaImpl
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceApiModelTest {
    @Test
    fun filterListKeepsOrderAndStates() {
        val text = object : Filter.Text("Query", "initial") {}
        val triState = object : Filter.TriState("Status", Filter.TriState.STATE_INCLUDE) {}
        val list = FilterList(text, triState)

        assertEquals(2, list.size)
        assertEquals("Query", list[0].name)
        assertEquals("initial", (list[0] as Filter.Text).state)
        assertTrue((list[1] as Filter.TriState).isIncluded())
        assertFalse(list[1].state == Filter.TriState.STATE_IGNORE)
    }

    @Test
    fun mangaDefaultsMatchTheSourceApiContract() {
        val manga = SMangaImpl()
        assertEquals(UpdateStrategy.ALWAYS_UPDATE, manga.update_strategy)
        assertFalse(manga.initialized)
        assertEquals(JsonObject.EMPTY, manga.memo)
        assertEquals(0, manga.status)
    }

    @Test
    fun chapterDefaultsStartUnnumberedAndUndated() {
        val chapter = SChapterImpl()
        assertEquals(-1f, chapter.chapter_number, 0f)
        assertEquals(0L, chapter.date_upload)
        assertEquals(JsonObject.EMPTY, chapter.memo)
    }
}
