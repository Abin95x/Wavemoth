package com.wavemoth.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wavemoth.app.MainActivity
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Catalog
import com.wavemoth.app.data.Radio
import com.wavemoth.app.data.YouTubeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the player so music keeps going with the app in the background or the screen off. */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val app = application as WavemothApp

        // Audio is small: start after 1s of buffer instead of the default 2.5s.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_000, 2_000)
            .build()

        // Same audio output as usual, plus a copy of the sound for the Home visualizer.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
                super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let(::SpectrumAudioSink)
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(AudioSources.forPlayback(app)))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Never shuffle behind the scenes: shuffle reorders the queue itself (see QueueOrder).
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))
        // Restore before adding listeners, so bringing back the last session isn't treated as a new play.
        app.lastSession.load()?.let { restore(player, it) }
        player.addListener(QueueListener(player))
        player.addListener(shuffler)
        player.addListener(SessionSaver(player))

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Puts back what was playing when the app was last closed, paused and not loaded, so it shows in
     * the mini player and starts from the same spot when you press play.
     */
    private fun restore(player: Player, saved: SavedSession) {
        player.repeatMode = saved.repeatMode
        player.shuffleModeEnabled = saved.shuffle
        shuffler.original = saved.unshuffled
        player.setMediaItems(saved.items, saved.index, saved.positionMs)
    }

    private val lastSession get() = (application as WavemothApp).lastSession

    /** Called when the app is swiped away from Recents. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null && player.mediaItemCount > 0) lastSession.savePosition(player)
        val stopOnSwipe = (application as WavemothApp).settings.stopOnSwipe.value
        if (player == null || stopOnSwipe || !player.playWhenReady || player.mediaItemCount == 0) {
            // Stopping the player also removes the media notification.
            player?.pause()
            player?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.player?.let { if (it.mediaItemCount > 0) lastSession.savePosition(it) }
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withSongUri() }.toMutableList())

        /** A new list while shuffle is on starts with the tapped song and shuffles the rest. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val items = mediaItems.map { it.withSongUri() }
            val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            if (!mediaSession.player.shuffleModeEnabled || items.isEmpty()) {
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, start, startPositionMs))
            }
            shuffler.original = items.map { it.mediaId }
            val order = QueueOrder.shuffle(items.size, start)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(order.map { items[it] }, 0, startPositionMs))
        }

        /** Play pressed with nothing loaded (e.g. a headphone button): carry on from the last session. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = lastSession.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            mediaSession.player.repeatMode = saved.repeatMode
            mediaSession.player.shuffleModeEnabled = saved.shuffle
            shuffler.original = saved.unshuffled
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs))
        }
    }

    /** Keeps [LastSession] up to date: the queue when it changes, the position every few seconds while playing. */
    private inner class SessionSaver(private val player: Player) : Player.Listener {
        private var ticker: Job? = null

        override fun onEvents(player: Player, events: Player.Events) {
            if (player.mediaItemCount == 0) return
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                lastSession.saveQueue((0 until player.mediaItemCount).map(player::getMediaItemAt), shuffler.original)
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) lastSession.savePosition(player)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                ticker?.cancel()
                ticker = if (!player.isPlaying) null else scope.launch {
                    while (isActive) {
                        delay(10_000)
                        lastSession.savePosition(player)
                    }
                }
            }
        }
    }

    private val shuffler = Shuffler()

    /** Turns shuffle on and off by reordering the queue, without interrupting the current song. */
    private inner class Shuffler : Player.Listener {
        /** Song ids in the order they had before shuffle was turned on. */
        var original: List<String> = emptyList()

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            val player = session?.player ?: return
            val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            if (items.isEmpty()) return
            val order = if (shuffleModeEnabled) {
                original = items.map { it.mediaId }
                QueueOrder.shuffle(items.size, player.currentMediaItemIndex)
            } else {
                QueueOrder.restore(items.map { it.mediaId }, original)
            }
            reorder(player, items, order)
        }

        /** Swaps every song except the playing one, so playback carries on untouched. */
        private fun reorder(player: Player, items: List<MediaItem>, order: List<Int>) {
            val current = player.currentMediaItemIndex
            val at = order.indexOf(current)
            if (at < 0) return
            player.removeMediaItems(current + 1, items.size)
            player.removeMediaItems(0, current)
            player.addMediaItems(0, order.subList(0, at).map { items[it] })
            player.addMediaItems(order.subList(at + 1, order.size).map { items[it] })
        }
    }

    /** Records history, preloads the next song, recovers from expired URLs and autoplays related songs. */
    private inner class QueueListener(private val player: ExoPlayer) : Player.Listener {
        private val retried = mutableSetOf<String>()
        private var fetchingRelated = false

        private val app get() = application as WavemothApp

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            app.library.addRecent(mediaItem.toSong())
            prefetchNext()
            maybeAppendRadio()
        }

        /** A jump to another song within 30 s of it starting counts as a skip. */
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val old = oldPosition.mediaItem ?: return
            if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return
            when (reason) {
                Player.DISCONTINUITY_REASON_SEEK ->
                    if (oldPosition.positionMs < 30_000 && newPosition.mediaItemIndex > oldPosition.mediaItemIndex) app.skips.add(old.toSong())
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> app.skips.forgive(old.mediaId)  // listened to the end
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val id = item.mediaId
            if (id !in retried) {
                // Usually an expired or rejected stream URL: fetch a fresh one and retry once.
                retried += id
                YouTubeSource.invalidate(id)
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        /** A restored session isn't loaded yet; load it as soon as anything asks it to play. */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) player.currentMediaItem?.let { retried -= it.mediaId }
        }

        /** Look up the next song's stream now, so skipping or the next track starts without a pause. */
        private fun prefetchNext() {
            val next = player.nextMediaItemIndex
            if (next == C.INDEX_UNSET) return
            val id = player.getMediaItemAt(next).mediaId
            scope.launch(Dispatchers.IO) { runCatching { YouTubeSource.resolveAudio(id) } }
        }

        /**
         * Autoplay: when two or fewer songs are left, add songs picked by [Radio] from what's related
         * to the last few songs, your likes, your history and your skips.
         */
        private fun maybeAppendRadio() {
            if (fetchingRelated || player.repeatMode != Player.REPEAT_MODE_OFF) return
            if (player.mediaItemCount - player.currentMediaItemIndex > 2) return
            val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            // Seeds: the last queued song and the two before it, most recent first.
            val seeds = queue.takeLast(3).reversed().map { it.mediaId }
            fetchingRelated = true
            scope.launch {
                val picks = runCatching {
                    withContext(Dispatchers.IO) {
                        val pools = seeds.map { id -> async { runCatching { Catalog.related(id) }.getOrDefault(emptyList()) } }.awaitAll()
                        val taste = Radio.taste(app.library.liked.value, app.library.recent.value, app.skips.all())
                        Radio.pick(pools, queue.mapTo(HashSet()) { it.mediaId }, taste, app.library.liked.value)
                    }
                }.getOrDefault(emptyList())
                if (picks.isNotEmpty()) player.addMediaItems(picks.map { it.toMediaItem(fromRadio = true) })
                fetchingRelated = false
            }
        }
    }
}
