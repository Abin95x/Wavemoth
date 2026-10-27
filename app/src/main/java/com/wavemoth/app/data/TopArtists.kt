package com.wavemoth.app.data

import java.util.concurrent.ConcurrentHashMap

/** An artist from your own library, for the Home "Artists" row. */
data class TopArtist(val name: String, val cover: String?)

object TopArtists {

    private val separators = Regex(",|&| x | feat\\.? | ft\\.? | and |/", RegexOption.IGNORE_CASE)

    /**
     * The artists you like and play most: a liked song counts 2, a recent play counts 1 (less the
     * older it is). Names keep their original spelling; the cover is from their most recent song.
     */
    fun from(liked: List<Song>, recent: List<Song>, limit: Int = 12): List<TopArtist> {
        val score = HashMap<String, Double>()
        val first = LinkedHashMap<String, TopArtist>()
        fun count(song: Song, weight: Double) = namesOf(song).forEach { name ->
            val key = name.lowercase()
            score.merge(key, weight, Double::plus)
            first.putIfAbsent(key, TopArtist(name, song.thumbnail.ifBlank { null }))
        }
        recent.forEachIndexed { i, s -> count(s, 1.0 / (1 + i * 0.05)) }
        liked.forEach { count(it, 2.0) }
        return first.entries
            .sortedByDescending { score.getValue(it.key) }
            .take(limit)
            .map { it.value }
    }

    /** "A, B & C feat. D - Topic" → [A, B, C, D]. */
    fun namesOf(song: Song): List<String> =
        song.artist.split(separators)
            .map { it.trim().removeSuffix(" - Topic").trim() }
            .filter { it.isNotEmpty() && !it.equals("unknown artist", ignoreCase = true) }
            .distinctBy { it.lowercase() }

    private val photos = ConcurrentHashMap<String, String>()

    /** The artist's photo from YouTube Music (cached), or null when it can't be found. Blocking. */
    fun photo(name: String): String? {
        photos[name.lowercase()]?.let { return it.ifBlank { null } }
        val found = runCatching {
            val results = Catalog.searchArtists(name)
            (results.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: results.firstOrNull())?.thumbnail
        }.getOrNull() ?: return null  // network error: try again next time
        photos[name.lowercase()] = found
        return found.ifBlank { null }
    }
}
