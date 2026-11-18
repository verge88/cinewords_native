package com.cinewords.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test fun parsesSrt() {
        val srt = """
            1
            00:00:01,200 --> 00:00:03,400
            Hello, world!

            2
            00:00:04,500 --> 00:00:06,000
            This is a test.
        """.trimIndent()
        val subs = SubtitleParser.parse(srt)
        assertEquals(2, subs.size)
        assertEquals(1200L, subs[0].startMs)
        assertEquals(3400L, subs[0].endMs)
        assertEquals("Hello, world!", subs[0].text)
        assertEquals("This is a test.", subs[1].text)
    }

    @Test fun parsesVttAndStripsTags() {
        val vtt = """
            WEBVTT

            00:02.500 --> 00:04.200 align:start
            <i>Thank</i> you &amp; goodbye
        """.trimIndent()
        val result = SubtitleParser.parse(vtt, "en")
        assertEquals(1, result.size)
        assertEquals(2500L, result[0].startMs)
        assertEquals("Thank you & goodbye", result[0].text)
    }

    @Test fun ignoresInvalidCues() {
        assertTrue(SubtitleParser.parse("WEBVTT\n\ninvalid").isEmpty())
    }

    @Test fun parsesYoutubeIds() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, extractYouTubeId(id))
        assertEquals(id, extractYouTubeId("https://youtu.be/" + id))
        assertEquals(id, extractYouTubeId("https://www.youtube.com/watch?v=" + id))
        assertEquals(id, extractYouTubeId("https://www.youtube.com/shorts/" + id))
    }

    @Test fun parsesDurations() {
        assertEquals(3723, CineApi.parseDuration("PT1H2M3S"))
        assertEquals(125, CineApi.parseDuration("PT2M5S"))
    }
}
