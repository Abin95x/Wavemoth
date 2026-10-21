package com.wavemoth.app

import com.wavemoth.app.data.PlaylistFile
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.SongMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistFileTest {

    @Test
    fun parsesQuotedFieldsAndRoundTrips() {
        val csv = "title,artist,album\n" +
            "\"Am I Dreaming (Metro Boomin & A\$AP Rocky, Roisee)\",\"Metro Boomin, A\$AP Rocky\",X\n" +
            "HUMBLE.,Kendrick Lamar,DAMN.\n"
        val rows = PlaylistFile.parse(csv)
        assertEquals(2, rows.size)
        assertEquals("Am I Dreaming (Metro Boomin & A\$AP Rocky, Roisee)", rows[0].title)
        assertEquals(listOf("Metro Boomin", "A\$AP Rocky"), rows[0].artists)
        assertEquals(rows, PlaylistFile.parse(PlaylistFile.write(rows)))
    }

    @Test
    fun cleanTitleDropsFeaturing() {
        assertEquals("Hot (Remix)", SongMatcher.cleanTitle("Hot (Remix) [feat. Gunna and Travis Scott]"))
        assertEquals("Hit 'Em Up", SongMatcher.cleanTitle("Hit 'Em Up - Single Version"))
    }

    @Test
    fun matcherPrefersRightArtistAndRejectsJunk() {
        val row = PlaylistFile.Row("SICKO MODE", "Travis Scott")
        val good = Song("aaaaaaaaaaa", "SICKO MODE", "Travis Scott", 312, "t")
        val cover = Song("bbbbbbbbbbb", "SICKO MODE (Cover)", "Someone", 300, "t")
        assertEquals("aaaaaaaaaaa", SongMatcher.find(row) { listOf(cover, good) }?.id)
        assertNull(SongMatcher.find(row) { listOf(Song("ccccccccccc", "Other Song", "Other", 100, "t")) })
    }
}
