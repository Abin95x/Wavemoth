package com.wavemoth.app.data

/** Ways to sort a song list. Lists are stored newest-added first, which gives the date orders. */
enum class SongSort(val label: String) {
    RecentlyAdded("Recently added"),
    OldestAdded("Oldest added"),
    Title("Title (A–Z)"),
    Artist("Artist (A–Z)"),
    Duration("Duration (shortest first)");

    fun apply(songs: List<Song>): List<Song> = when (this) {
        RecentlyAdded -> songs
        OldestAdded -> songs.asReversed()
        Title -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() })
        Artist -> songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.trim() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() })
        Duration -> songs.sortedBy { it.durationSec }
    }

    companion object {
        fun fromName(name: String?): SongSort = entries.firstOrNull { it.name == name } ?: RecentlyAdded
    }
}
