package com.wavemoth.app

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wavemoth.app.FakePlayer.Companion.song
import com.wavemoth.app.playback.LastSession
import com.wavemoth.app.playback.isFromRadio
import com.wavemoth.app.playback.toMediaItem
import com.wavemoth.app.playback.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LastSessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun nothingSavedMeansNothingToResume() {
        assertNull(LastSession(context).load())
    }

    @Test fun queueSongAndPositionComeBack() {
        val songs = listOf(song(1), song(2), song(3))
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItems(
            songs.dropLast(1).map { it.toMediaItem() } + songs.last().toMediaItem(fromRadio = true),
            1, 42_000,
        )
        player.shuffleModeEnabled = true
        player.repeatMode = Player.REPEAT_MODE_ONE

        LastSession(context).apply {
            save(player, unshuffled = listOf(songs[2].id, songs[0].id, songs[1].id))
            source = "Liked Songs"
        }
        player.release()

        val restored = LastSession(context)
        val saved = restored.load()!!
        assertEquals(songs, saved.items.map { it.toSong() })
        assertEquals(listOf(false, false, true), saved.items.map { it.isFromRadio })
        assertEquals(1, saved.index)
        assertEquals(42_000L, saved.positionMs)
        assertTrue(saved.shuffle)
        assertEquals(Player.REPEAT_MODE_ONE, saved.repeatMode)
        assertEquals(listOf(songs[2].id, songs[0].id, songs[1].id), saved.unshuffled)
        assertEquals("Liked Songs", restored.source)
    }

    @Test fun savedIndexIsKeptInsideTheQueue() {
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItems(listOf(song(1).toMediaItem(), song(2).toMediaItem()), 1, 0)
        val session = LastSession(context)
        session.save(player, emptyList())
        // The queue later shrinks to one song while the stored index still says 1.
        session.saveQueue(listOf(song(1).toMediaItem()), emptyList())
        player.release()

        val saved = session.load()!!
        assertEquals(0, saved.index)
        assertFalse(saved.shuffle)
    }
}
