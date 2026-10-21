package com.wavemoth.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

        val player = ExoPlayer.Builder(this)
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
        player.addListener(QueueListener(player))

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Called when the app is swiped away from Recents. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val stopOnSwipe = (application as WavemothApp).settings.stopOnSwipe.value
        if (player == null || stopOnSwipe || !player.playWhenReady || player.mediaItemCount == 0) {
            // Stopping the player also removes the media notification.
            player?.pause()
            player?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withSongUri() }.toMutableList())
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
                if (picks.isNotEmpty()) player.addMediaItems(picks.map { it.toMediaItem() })
                fetchingRelated = false
            }
        }
    }
}
