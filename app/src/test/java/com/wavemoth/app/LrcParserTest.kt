package com.wavemoth.app

import com.wavemoth.app.data.LrcParser
import com.wavemoth.app.data.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses placeholder text only. */
class LrcParserTest {

    @Test
    fun parsesTimestampsInAllFormats() {
        val lrc = """
            [ar:Someone]
            [00:01.50] First line
            [00:03.2]Second line
            [01:02.345] Third line
            [02:00] Fourth line
            untimed text is skipped
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(1_500, "First line"),
                LyricLine(3_200, "Second line"),
                LyricLine(62_345, "Third line"),
                LyricLine(120_000, "Fourth line"),
            ),
            LrcParser.parse(lrc),
        )
    }

    @Test
    fun repeatedLineWithSeveralTimestampsIsSorted() {
        val lines = LrcParser.parse("[00:10.00][00:30.00] Chorus line\n[00:20.00] Verse line")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lines.map { it.timeMs })
        assertEquals("Chorus line", lines[2].text)
    }

    @Test
    fun currentIndexFollowsPosition() {
        val lines = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, LrcParser.currentIndex(lines, 500))
        assertEquals(0, LrcParser.currentIndex(lines, 1_000))
        assertEquals(1, LrcParser.currentIndex(lines, 8_999))
        assertEquals(2, LrcParser.currentIndex(lines, 60_000))
        assertEquals(-1, LrcParser.currentIndex(emptyList(), 1_000))
    }
}
