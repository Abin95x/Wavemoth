package com.wavemoth.app

import com.wavemoth.app.data.Radio
import com.wavemoth.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RadioTest {

    private fun s(id: String, artist: String, title: String = "Song $id", sec: Long = 200) =
        Song(id.padEnd(11, '0'), title, artist, sec, "")

    private val noTaste = Radio.taste(emptyList(), emptyList(), emptyList())

    @Test
    fun excludesQueuedRecentSkippedAndJunk() {
        val pool = listOf(
            s("queued", "A"), s("recent", "B"), s("skipped", "C"),
            s("mix", "D", title = "Best Hits Mix 2026"), s("cover", "E", title = "Famous Song (Cover)"),
            s("short", "F", sec = 30), s("long", "G", sec = 3600),
            s("good1", "H"), s("good2", "I"),
        )
        val taste = Radio.taste(liked = emptyList(), recent = listOf(s("recent", "B")), skipped = listOf(s("skipped", "C")))
        val picks = Radio.pick(listOf(pool), queueIds = setOf(s("queued", "A").id), taste = taste, likedSongs = emptyList(), random = Random(1))
        assertEquals(setOf(s("good1", "H").id, s("good2", "I").id), picks.map { it.id }.toSet())
    }

    @Test
    fun keepsArtistsVariedAndCapped() {
        val pool = (1..6).map { s("x$it", "Same Artist") } + (1..4).map { s("y$it", "Other $it") }
        val picks = Radio.pick(listOf(pool), emptySet(), noTaste, emptyList(), count = 10, random = Random(2))
        assertTrue(picks.count { it.artist == "Same Artist" } <= 2)
        picks.zipWithNext().forEach { (a, b) -> assertFalse("same artist back to back", a.artist == b.artist) }
    }

    @Test
    fun songsRelatedToSeveralSeedsAndLikedArtistsComeFirst() {
        val shared = s("shared", "Shared")
        val liked = s("liked", "Fav Artist")
        val pools = listOf(
            listOf(s("a1", "P"), s("a2", "Q"), shared, s("a3", "R")),
            listOf(s("b1", "S"), shared),
            listOf(s("c1", "T"), s("c2", "Fav Artist")),
        )
        val taste = Radio.taste(liked = listOf(liked), recent = emptyList(), skipped = emptyList())
        val picks = Radio.pick(pools, emptySet(), taste, likedSongs = emptyList(), count = 3, random = Random(3))
        assertTrue("liked artist should rank high", s("c2", "Fav Artist").id in picks.map { it.id })
        assertTrue("song related to two seeds should rank high", shared.id in picks.map { it.id })
    }

    @Test
    fun slipsInAFamiliarLikedSongEveryFewPicks() {
        val pool = (1..10).map { s("p$it", "Artist $it") }
        val liked = s("fav", "Artist 3")  // liked, by an artist that appears among candidates
        val taste = Radio.taste(liked = listOf(liked), recent = emptyList(), skipped = emptyList())
        val picks = Radio.pick(listOf(pool), emptySet(), taste, likedSongs = listOf(liked), count = 8, random = Random(4))
        assertTrue(liked.id in picks.map { it.id })
        assertEquals(8, picks.size)
        assertEquals(picks.size, picks.map { it.id }.toSet().size)  // no duplicates
    }

    @Test
    fun splitsCollaborationArtists() {
        assertEquals(listOf("metro boomin", "future", "chris brown"), Radio.artistsOf(s("z", "Metro Boomin, Future & Chris Brown")))
        assertEquals(listOf("artist"), Radio.artistsOf(s("z", "Artist - Topic")))
    }
}
