package com.wavemoth.app.playback

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wavemoth.app.data.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything about playback except the moving position (see [PlaybackProgress]). */
@Immutable
data class PlayerUiState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    /** The songs a swipe would go to (shuffle and repeat taken into account). */
    val previousSong: Song? = null,
    val nextSong: Song? = null,
    val source: String = "",
    val error: String? = null,
)

/**
 * A position sample. The UI extrapolates from it every frame while playing, so progress bars move
 * smoothly without the rest of the screen recomposing.
 */
@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val sampledAt: Long = SystemClock.elapsedRealtime(),
) {
    fun positionAt(now: Long): Long {
        val p = if (playing) positionMs + (now - sampledAt).coerceAtLeast(0) else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** What the UI can see and do with the player. Lets screens be tested with a fake player. */
interface PlayerControls {
    val state: StateFlow<PlayerUiState>
    val progress: StateFlow<PlaybackProgress>

    /** Replace the queue with [songs] and start at [index]. [source] is shown as "Playing from". */
    fun play(songs: List<Song>, index: Int = 0, source: String = "")
    fun playNext(song: Song)
    fun addToQueue(song: Song)
    fun togglePlay()
    fun next()
    /** Previous button: restarts the song if it's a few seconds in, else goes back one. */
    fun previous()
    /** Always goes to the previous song (swipe). */
    fun skipToPrevious()
    fun skipTo(index: Int)
    fun seekTo(ms: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun clearError()
}

/** The UI's handle on the playback service. */
class PlayerViewModel(app: Application) : AndroidViewModel(app), PlayerControls {

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    override val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val controllerReady = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Rebuilding the queue list is the costly part; only do it when the queue changed.
            refresh(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.cause?.message ?: error.message ?: "Playback error") }
        }
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            controllerReady.complete(c)
            refresh(rebuildQueue = true)
            // Occasional re-sync; between samples the UI extrapolates on its own.
            while (isActive) {
                if (c.isPlaying) sampleProgress(c)
                delay(2_000)
            }
        }
    }

    private fun sampleProgress(c: Player) {
        _progress.value = PlaybackProgress(
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0),
            playing = c.isPlaying,
        )
    }

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        sampleProgress(c)
        _state.update {
            it.copy(
                current = c.currentMediaItem?.toSong(),
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = if (rebuildQueue) (0 until c.mediaItemCount).map { i -> c.getMediaItemAt(i).toSong() } else it.queue,
                queueIndex = c.currentMediaItemIndex,
                previousSong = c.previousMediaItemIndex.takeIf { i -> i != C.INDEX_UNSET }?.let { i -> c.getMediaItemAt(i).toSong() },
                nextSong = c.nextMediaItemIndex.takeIf { i -> i != C.INDEX_UNSET }?.let { i -> c.getMediaItemAt(i).toSong() },
            )
        }
    }

    private fun withController(block: MediaController.() -> Unit) {
        viewModelScope.launch { controllerReady.await().block() }
    }

    override fun play(songs: List<Song>, index: Int, source: String) {
        if (songs.isEmpty()) return
        _state.update { it.copy(source = source, error = null) }
        withController {
            setMediaItems(songs.map { it.toMediaItem() }, index.coerceIn(songs.indices), 0)
            prepare()
            play()
        }
    }

    override fun playNext(song: Song) = withController {
        if (mediaItemCount == 0) play(listOf(song)) else addMediaItem(currentMediaItemIndex + 1, song.toMediaItem())
    }

    override fun addToQueue(song: Song) = withController {
        if (mediaItemCount == 0) play(listOf(song)) else addMediaItem(song.toMediaItem())
    }

    override fun togglePlay() {
        // Flip the icon immediately; the player confirms a moment later.
        _state.update { it.copy(isPlaying = !it.isPlaying) }
        withController {
            if (isPlaying) pause() else {
                if (playbackState == Player.STATE_IDLE) prepare()
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition(0)
                play()
            }
        }
    }

    override fun next() = withController { seekToNext() }
    override fun previous() = withController { seekToPrevious() }
    override fun skipToPrevious() = withController { seekToPreviousMediaItem() }
    override fun skipTo(index: Int) = withController { seekToDefaultPosition(index); play() }

    override fun seekTo(ms: Long) {
        _progress.update { it.copy(positionMs = ms, sampledAt = SystemClock.elapsedRealtime()) }
        withController { seekTo(ms) }
    }

    override fun toggleShuffle() = withController { shuffleModeEnabled = !shuffleModeEnabled }

    override fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun clearError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        controller?.run {
            removeListener(listener)
            release()
        }
        super.onCleared()
    }
}
