package com.wavemoth.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.wavemoth.app.data.songFromJson
import com.wavemoth.app.data.toJson
import org.json.JSONArray
import org.json.JSONObject

/** What was playing when the app was last closed, so it can be picked up again. */
data class SavedSession(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** Song ids in their order before shuffle was turned on (see [QueueOrder.restore]). */
    val unshuffled: List<String>,
    val repeatMode: Int,
)

/**
 * Remembers the queue, the song and the position. The queue is written only when it changes;
 * the position is cheap and written often.
 */
class LastSession(context: Context) {
    private val prefs = context.getSharedPreferences("last_session", Context.MODE_PRIVATE)

    /** "Playing from" label shown on the player. */
    var source: String
        get() = prefs.getString(KEY_SOURCE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SOURCE, value).apply()

    fun saveQueue(items: List<MediaItem>, unshuffled: List<String>) {
        val queue = JSONArray(items.map { item ->
            item.toSong().toJson().apply { if (item.isFromRadio) put("radio", true) }
        })
        prefs.edit()
            .putString(KEY_QUEUE, queue.toString())
            .putString(KEY_UNSHUFFLED, JSONArray(unshuffled).toString())
            .apply()
    }

    fun savePosition(player: Player) {
        prefs.edit()
            .putInt(KEY_INDEX, player.currentMediaItemIndex)
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0))
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .putInt(KEY_REPEAT, player.repeatMode)
            .apply()
    }

    fun save(player: Player, unshuffled: List<String>) {
        saveQueue((0 until player.mediaItemCount).map(player::getMediaItemAt), unshuffled)
        savePosition(player)
    }

    fun load(): SavedSession? = runCatching {
        val queue = JSONArray(prefs.getString(KEY_QUEUE, null) ?: return null)
        val items = (0 until queue.length()).map { i ->
            val o: JSONObject = queue.getJSONObject(i)
            songFromJson(o).toMediaItem(fromRadio = o.optBoolean("radio"))
        }
        if (items.isEmpty()) return null
        val unshuffled = JSONArray(prefs.getString(KEY_UNSHUFFLED, "[]"))
        SavedSession(
            items = items,
            index = prefs.getInt(KEY_INDEX, 0).coerceIn(items.indices),
            positionMs = prefs.getLong(KEY_POSITION, 0),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            unshuffled = (0 until unshuffled.length()).map(unshuffled::getString),
            repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF),
        )
    }.getOrNull()

    private companion object {
        const val KEY_QUEUE = "queue"
        const val KEY_UNSHUFFLED = "unshuffled"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position_ms"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_SOURCE = "source"
    }
}
