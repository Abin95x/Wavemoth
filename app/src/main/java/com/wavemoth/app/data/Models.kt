package com.wavemoth.app.data

data class Song(
    val id: String,          // YouTube video id
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnail: String,
)

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val createdAt: Long,
)

fun Long.formatDuration(): String {
    if (this <= 0) return ""
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
