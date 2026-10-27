package com.wavemoth.app

import com.wavemoth.app.data.Song
import com.wavemoth.app.data.SongSort
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSortTest {

    // Stored newest-added first, like Liked Songs.
    private val songs = listOf(
        Song("c", "beta", "Zed", 300, ""),
        Song("b", "Alpha", "amy", 200, ""),
        Song("a", "gamma", "Amy", 100, ""),
    )

    @Test
    fun sortsByEachOption() {
        assertEquals(listOf("c", "b", "a"), SongSort.RecentlyAdded.apply(songs).map { it.id })
        assertEquals(listOf("a", "b", "c"), SongSort.OldestAdded.apply(songs).map { it.id })
        assertEquals(listOf("b", "c", "a"), SongSort.Title.apply(songs).map { it.id })  // ignores case
        assertEquals(listOf("b", "a", "c"), SongSort.Artist.apply(songs).map { it.id })  // then by title
        assertEquals(listOf("a", "b", "c"), SongSort.Duration.apply(songs).map { it.id })
    }

    @Test
    fun unknownSavedValueFallsBackToRecentlyAdded() {
        assertEquals(SongSort.RecentlyAdded, SongSort.fromName("nope"))
        assertEquals(SongSort.Title, SongSort.fromName("Title"))
    }
}
