package com.wavemoth.app.data

import android.content.Context
import org.json.JSONArray

/** Songs you skipped early (within 30 s). Used by [Radio] to avoid playing them again. */
class SkipHistory(context: Context) {
    private val prefs = context.getSharedPreferences("skips", Context.MODE_PRIVATE)
    private val skipped = ArrayDeque<Song>()

    init {
        runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) skipped.addLast(songFromJson(arr.getJSONObject(i)))
        }
    }

    @Synchronized fun all(): List<Song> = skipped.toList()

    @Synchronized fun add(song: Song) {
        skipped.removeAll { it.id == song.id }
        skipped.addFirst(song)
        while (skipped.size > MAX) skipped.removeLast()
        prefs.edit().putString(KEY, JSONArray(skipped.map { it.toJson() }).toString()).apply()
    }

    /** Liking a song (or finishing it) means it shouldn't count as skipped any more. */
    @Synchronized fun forgive(id: String) {
        if (skipped.removeAll { it.id == id }) {
            prefs.edit().putString(KEY, JSONArray(skipped.map { it.toJson() }).toString()).apply()
        }
    }

    private companion object {
        const val KEY = "skipped"
        const val MAX = 200
    }
}
