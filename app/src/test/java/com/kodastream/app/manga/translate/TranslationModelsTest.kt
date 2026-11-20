package com.kodastream.app.manga.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationModelsTest {

    @Test
    fun fencedJsonWithExtraTextIsParsed() {
        val raw = """
            Here is the result:
            ```json
            {"blocks":[{"box_2d":[10,20,300,400],"text":"Hello","translation":"مرحبا","type":"dialogue"}]}
            ```
        """.trimIndent()

        val blocks = TranslationJson.parseBlocks(raw)

        assertEquals(1, blocks.size)
        assertEquals("مرحبا", blocks[0].translation)
    }

    @Test
    fun blankTranslationsAndUsableBoxesAreDropped() {
        val raw = """
            {"blocks":[
              {"box_2d":[10,20,300,400],"translation":" "},
              {"box_2d":[10,20,10,400],"translation":"tiny"},
              {"box_2d":[10,20,300,400],"translation":"kept"}
            ]}
        """.trimIndent()

        val blocks = TranslationJson.parseBlocks(raw)

        assertEquals(listOf("kept"), blocks.map { it.translation })
    }

    @Test
    fun boxIsNormalizedAndSwappedWhenCornersAreReversed() {
        val block = TranslatedBlock(box = listOf(500f, 800f, 100f, 200f), translation = "x")

        val box = block.normalizedBox()!!

        assertEquals(0.2f, box[0], 0.001f)
        assertEquals(0.1f, box[1], 0.001f)
        assertEquals(0.8f, box[2], 0.001f)
        assertEquals(0.5f, box[3], 0.001f)
    }

    @Test
    fun boxOutsideTheImageIsClamped() {
        val block = TranslatedBlock(box = listOf(-200f, -200f, 1400f, 1400f), translation = "x")

        val box = block.normalizedBox()!!

        assertEquals(0f, box[0], 0.001f)
        assertEquals(0f, box[1], 0.001f)
        assertEquals(1f, box[2], 0.001f)
        assertEquals(1f, box[3], 0.001f)
    }

    @Test
    fun unusableBoxesReturnNull() {
        assertNull(TranslatedBlock(box = listOf(1f, 2f), translation = "x").normalizedBox())
        assertNull(
            TranslatedBlock(
                box = listOf(10f, 10f, 10f, 10f),
                translation = "x",
            ).normalizedBox(),
        )
    }

    @Test
    fun sliceBoxesMoveIntoTheFullPage() {
        val block = TranslatedBlock(box = listOf(0f, 0f, 500f, 1000f), translation = "x")

        val moved = block.moveToPage(sliceTop = 1000, sliceHeight = 1000, pageHeight = 4000)!!

        assertEquals(250f, moved.box[0], 0.001f)
        assertEquals(0f, moved.box[1], 0.001f)
        assertEquals(375f, moved.box[2], 0.001f)
        assertEquals(1000f, moved.box[3], 0.001f)
        assertEquals(0f, moved.normalizedBox()!![0], 0.001f)
        assertEquals(0.25f, moved.normalizedBox()!![1], 0.001f)
        assertEquals(0.375f, moved.normalizedBox()!![3], 0.001f)
    }

    @Test
    fun oldCacheVersionIsIgnored() {
        assertNull(TranslationJson.decode("""{"version":0,"blocks":[]}"""))
    }
}
