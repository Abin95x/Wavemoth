package com.wavemoth.app.data

import java.text.Normalizer
import kotlin.math.abs

/**
 * Playlist files: plain CSV with a header row. Columns (any order, case-insensitive):
 *   title, artist, album            – required: title; artist strongly recommended
 *   youtube_id, duration, thumbnail – optional; filled in once a song has been matched
 * Pure JVM so it can be unit tested.
 */
object PlaylistFile {

    data class Row(
        val title: String,
        val artist: String,
        val album: String = "",
        val youtubeId: String? = null,
        val durationSec: Long = 0,
        val thumbnail: String? = null,
    ) {
        val artists: List<String> get() = artist.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        /** Already matched rows play directly, with no search needed. */
        fun toSongOrNull(): Song? = youtubeId?.takeIf { it.length == 11 }?.let {
            Song(it, title, artist, durationSec, YouTubeSource.artworkUrl(thumbnail, it))
        }
    }

    fun parse(text: String): List<Row> {
        val lines = parseCsv(text.removePrefix("﻿")).filter { r -> r.any { it.isNotBlank() } }
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().map { it.trim().lowercase() }
        val hasHeader = "title" in header
        val col = { name: String, fallback: Int -> if (hasHeader) header.indexOf(name) else fallback }
        val iTitle = col("title", 0)
        val iArtist = col("artist", 1)
        val iAlbum = col("album", 2)
        val iId = col("youtube_id", -1)
        val iDur = col("duration", -1)
        val iThumb = col("thumbnail", -1)
        return lines.drop(if (hasHeader) 1 else 0).mapNotNull { r ->
            fun at(i: Int) = r.getOrNull(i)?.trim().orEmpty()
            val title = at(iTitle).ifBlank { return@mapNotNull null }
            Row(
                title = title,
                artist = at(iArtist),
                album = at(iAlbum),
                youtubeId = at(iId).ifBlank { null },
                durationSec = at(iDur).toLongOrNull() ?: 0,
                thumbnail = at(iThumb).ifBlank { null },
            )
        }
    }

    fun fromSongs(songs: List<Song>): String = write(songs.map {
        Row(it.title, it.artist, youtubeId = it.id, durationSec = it.durationSec, thumbnail = it.thumbnail)
    })

    fun write(rows: List<Row>): String = buildString {
        appendLine("title,artist,album,youtube_id,duration,thumbnail")
        rows.forEach { r ->
            appendLine(listOf(r.title, r.artist, r.album, r.youtubeId.orEmpty(), if (r.durationSec > 0) r.durationSec.toString() else "", r.thumbnail.orEmpty()).joinToString(",") { quote(it) })
        }
    }

    private fun quote(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** Minimal RFC 4180 reader: quoted fields, doubled quotes, commas and newlines inside quotes. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}

/** Finds the YouTube Music version of a song given its title and artists. */
object SongMatcher {

    fun find(row: PlaylistFile.Row, search: (String) -> List<Song> = { Catalog.search(it).songs }): Song? {
        val query = listOf(cleanTitle(row.title), row.artists.firstOrNull().orEmpty()).joinToString(" ").trim()
        val candidates = runCatching { search(query).take(8) }.getOrNull().orEmpty()
        val best = candidates.minByOrNull { score(row, it) } ?: return null
        if (score(row, best) > REJECT_ABOVE) return null
        // YouTube supplies the playable id and cover; keep the file's own names.
        return best.copy(title = row.title, artist = row.artist.ifBlank { best.artist })
    }

    /** Lower is better. */
    fun score(row: PlaylistFile.Row, song: Song): Int {
        var score = 0
        if (row.durationSec > 0 && song.durationSec > 0) {
            score += abs(song.durationSec - row.durationSec).toInt().coerceAtMost(60)
        }
        val songArtist = normalize(song.artist)
        val artistHit = row.artists.any { a -> normalize(a).let { it.isNotEmpty() && (songArtist.contains(it) || it.contains(songArtist)) } }
        if (!artistHit) score += 30

        val wanted = words(row.title)
        val got = words(song.title)
        if (wanted.isNotEmpty()) score += wanted.count { it !in got } * 40 / wanted.size

        // Avoid covers, sped-up and similar versions unless the title asks for them.
        for (tag in listOf("cover", "karaoke", "sped up", "slowed", "reverb", "8d", "instrumental", "nightcore")) {
            if (tag in normalize(song.title) && tag !in normalize(row.title)) score += 25
        }
        return score
    }

    private const val REJECT_ABOVE = 60

    /** Drops "(feat. …)", "[with …]" so the search focuses on the song name. */
    fun cleanTitle(title: String): String =
        title.replace(Regex("[(\\[]\\s*(feat|ft|with)\\b[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+Single Version$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun words(title: String): Set<String> =
        normalize(cleanTitle(title))
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .split(Regex("[^\\p{L}\\p{N}$!]+"))
            .filter { it.length > 1 || it.firstOrNull()?.isDigit() == true }
            .toSet()
}
