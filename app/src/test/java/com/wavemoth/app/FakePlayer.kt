package com.wavemoth.app

import androidx.media3.common.Player
import com.wavemoth.app.data.Song
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.playback.PlayerControls
import com.wavemoth.app.playback.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow

/** Stands in for the real player in UI tests and records every call the screens make. */
class FakePlayer : PlayerControls {
    override val state = MutableStateFlow(PlayerUiState())
    override val progress = MutableStateFlow(PlaybackProgress())
    val calls = mutableListOf<String>()
    var lastPlay: Triple<List<Song>, Int, String>? = null

    override fun play(songs: List<Song>, index: Int, source: String) {
        calls += "play"
        lastPlay = Triple(songs, index, source)
        state.value = withNeighbours(state.value.copy(current = songs[index], isPlaying = true, queue = songs, queueIndex = index, source = source))
    }

    /** Moves through the queue like the real player, so swipes and buttons change the song. */
    private fun moveTo(index: Int) {
        val s = state.value
        if (index !in s.queue.indices) return
        state.value = withNeighbours(s.copy(current = s.queue[index], queueIndex = index))
    }

    override fun playNext(song: Song) { calls += "playNext:${song.id}" }
    override fun addToQueue(song: Song) { calls += "addToQueue:${song.id}" }

    override fun togglePlay() {
        calls += "togglePlay"
        state.value = state.value.copy(isPlaying = !state.value.isPlaying)
    }

    override fun next() { calls += "next"; moveTo(state.value.queueIndex + 1) }
    override fun previous() { calls += "previous"; moveTo(state.value.queueIndex - 1) }
    override fun skipToPrevious() { calls += "skipToPrevious"; moveTo(state.value.queueIndex - 1) }
    override fun skipTo(index: Int) { calls += "skipTo:$index" }
    override fun seekTo(ms: Long) { calls += "seekTo:$ms" }

    override fun toggleShuffle() {
        calls += "toggleShuffle"
        state.value = state.value.copy(shuffle = !state.value.shuffle)
    }

    override fun cycleRepeat() {
        calls += "cycleRepeat"
        state.value = state.value.copy(repeatMode = (state.value.repeatMode + 1) % 3)
    }

    override fun clearError() {
        calls += "clearError"
        state.value = state.value.copy(error = null)
    }

    companion object {
        /** Fills in the songs on either side of the current one. */
        fun withNeighbours(s: PlayerUiState) = s.copy(
            previousSong = s.queue.getOrNull(s.queueIndex - 1),
            nextSong = s.queue.getOrNull(s.queueIndex + 1),
        )

        /** Test songs with no artwork URL, so nothing touches the network. */
        fun song(n: Int, title: String = "Test Song $n", artist: String = "Test Artist") =
            Song("vid${n.toString().padStart(8, '0')}", title, artist, 180L + n, "")

        val REPEAT_MODES = listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)
    }
}
