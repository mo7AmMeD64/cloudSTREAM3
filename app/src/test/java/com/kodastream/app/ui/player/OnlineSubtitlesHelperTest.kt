package com.kodastream.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OnlineSubtitlesHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseSubdlResponse_parsesEntriesCorrectly() {
        val json = """
            {
                "status": true,
                "subtitles": [
                    {
                        "release_name": "Inception.1080p.BluRay",
                        "language": "English",
                        "url": "/subtitle/123.zip"
                    },
                    {
                        "name": "Inception.720p.WEB-DL",
                        "language": "Spanish",
                        "url": "https://dl.subdl.com/subtitle/456.srt"
                    }
                ]
            }
        """.trimIndent()

        val results = OnlineSubtitlesHelper.parseSubdlResponse(json)
        assertEquals(2, results.size)
        assertEquals("Inception.1080p.BluRay", results[0].name)
        assertEquals("English", results[0].language)
        assertEquals("https://dl.subdl.com/subtitle/123.zip", results[0].url)
        assertEquals("SubDL", results[0].source)

        assertEquals("Inception.720p.WEB-DL", results[1].name)
        assertEquals("Spanish", results[1].language)
        assertEquals("https://dl.subdl.com/subtitle/456.srt", results[1].url)
    }

    @Test
    fun parseSubdlResponse_handlesFailureStatus() {
        val json = """{"status": false, "error": "not_found"}"""
        val results = OnlineSubtitlesHelper.parseSubdlResponse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun parseOpenSubtitlesResponse_parsesEntriesCorrectly() {
        val json = """
            {
                "subtitles": [
                    {
                        "id": "101",
                        "url": "https://subs.example.com/101.srt",
                        "movieReleaseName": "Breaking.Bad.S01E01.720p",
                        "lang": "en"
                    },
                    {
                        "id": "102",
                        "url": "https://subs.example.com/102.srt",
                        "subtitleFileName": "bb_s01e01_es.srt",
                        "lang": "es"
                    }
                ]
            }
        """.trimIndent()

        val results = OnlineSubtitlesHelper.parseOpenSubtitlesResponse(json)
        assertEquals(2, results.size)
        assertEquals("Breaking.Bad.S01E01.720p", results[0].name)
        assertEquals("en", results[0].language)
        assertEquals("https://subs.example.com/101.srt", results[0].url)
        assertEquals("OpenSubtitles", results[0].source)

        assertEquals("bb_s01e01_es.srt", results[1].name)
        assertEquals("es", results[1].language)
    }

    @Test
    fun extractSubtitleFromZip_extractsSrtCorrectly() {
        val zipBytes = createSampleZip("movie_sub.srt", "1\n00:00:01,000 --> 00:00:04,000\nHello World\n")
        val outFile = tempFolder.newFile("extracted.srt")

        val extracted = OnlineSubtitlesHelper.extractSubtitleFromZip(zipBytes, outFile)
        assertTrue(extracted)
        assertTrue(outFile.exists())
        assertEquals("1\n00:00:01,000 --> 00:00:04,000\nHello World\n", outFile.readText())
    }

    @Test
    fun extractSubtitleFromZip_returnsFalseForNonZipBytes() {
        val rawBytes = "Regular text file content".toByteArray(Charsets.UTF_8)
        val outFile = tempFolder.newFile("dummy.srt")

        val extracted = OnlineSubtitlesHelper.extractSubtitleFromZip(rawBytes, outFile)
        assertFalse(extracted)
    }

    private fun createSampleZip(entryName: String, content: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }
}
