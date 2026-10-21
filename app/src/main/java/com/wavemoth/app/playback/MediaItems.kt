package com.wavemoth.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.wavemoth.app.data.Song

/** Queue items use this scheme; the player swaps it for a real stream URL just before loading. */
const val SONG_SCHEME = "wavemoth"
private const val EXTRA_DURATION = "duration_sec"

fun songUri(id: String): Uri = Uri.parse("$SONG_SCHEME://song/$id")

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(songUri(id))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(thumbnail))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationSec) })
            .build(),
    )
    .build()

fun MediaItem.toSong(): Song = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION) ?: 0L,
    thumbnail = mediaMetadata.artworkUri?.toString().orEmpty(),
)

/** Items sent from the UI lose their URI on the way to the service; put it back. */
fun MediaItem.withSongUri(): MediaItem =
    if (localConfiguration != null) this else buildUpon().setUri(songUri(mediaId)).build()
