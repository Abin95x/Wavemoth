package com.wavemoth.app.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics for one song. [synced] has timestamps (line-by-line highlighting); [plain] is the fallback. */
data class Lyrics(
    val synced: List<LyricLine>,
    val plain: String,
    val instrumental: Boolean,
) {
    val isEmpty get() = synced.isEmpty() && plain.isBlank() && !instrumental
}

/** Reads LRC text: lines like `[01:23.45] words`, possibly several timestamps per line. Pure JVM. */
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(lrc: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue  // metadata like [ar:…] or untimed text
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val (min, sec, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                out += LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }
}

/**
 * Lyrics from LRCLIB (lrclib.net): free, no account or key, community-contributed and
 * mostly time-synced. Blocking calls: use off the main thread.
 */
object LrcLib {
    @Volatile var client: OkHttpClient = OkHttpClient()
    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "Wavemoth/1.0 (personal music player)"
    private val cache = object : LinkedHashMap<String, Lyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>?) = size > 60
    }

    /** Lyrics for [song], or null if LRCLIB has none. Results (including misses) are cached. */
    fun lyricsFor(song: Song): Lyrics? {
        synchronized(cache) { if (cache.containsKey(song.id)) return cache[song.id] }
        val title = SongMatcher.cleanTitle(song.title)
        val artist = song.artist.split(',').first().trim()
        val result = exact(title, artist, song.durationSec) ?: search(title, artist, song.durationSec)
        synchronized(cache) { cache[song.id] = result }
        return result
    }

    private fun exact(title: String, artist: String, durationSec: Long): Lyrics? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (durationSec > 0) addQueryParameter("duration", durationSec.toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        return toLyrics(JSONObject(body))
    }

    /** Fuzzy fallback: prefer synced results whose length is closest to the song's. */
    private fun search(title: String, artist: String, durationSec: Long): Lyrics? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = get(url.toString()) ?: return null
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= 8 }
        val best = candidates.minByOrNull { r ->
            val noSync = if (r.optString("syncedLyrics").isNullOrBlank() || r.isNull("syncedLyrics")) 100 else 0
            noSync + abs(r.optDouble("duration", 0.0) - durationSec).toInt()
        } ?: return null
        return toLyrics(best)
    }

    private fun toLyrics(o: JSONObject): Lyrics? {
        val synced = if (o.isNull("syncedLyrics")) "" else o.optString("syncedLyrics")
        val plain = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics")
        val lyrics = Lyrics(LrcParser.parse(synced), plain, o.optBoolean("instrumental"))
        return lyrics.takeUnless { it.isEmpty }
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw java.io.IOException("Lyrics service error ${resp.code}")
            return resp.body?.string()
        }
    }
}
