package com.wavemoth.app.download

import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import coil.imageLoader
import coil.request.ImageRequest
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.LibraryRepository
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.songFromJson
import com.wavemoth.app.data.toJson
import com.wavemoth.app.playback.songUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.Executors

enum class DownloadStatus { Queued, Downloading, Done, Failed }

data class DownloadEntry(val song: Song, val status: DownloadStatus, val addedAt: Long)

/**
 * Songs saved on the phone for offline listening. Audio goes to its own store (never evicted) that
 * playback reads before anything else. Downloads keep going in the background through
 * [DownloadsService] and pick up where they left off after the app restarts.
 */
@UnstableApi
class Downloads(private val app: WavemothApp) {

    private val main = Handler(Looper.getMainLooper())

    private val _entries = MutableStateFlow<Map<String, DownloadEntry>>(emptyMap())
    private val _status = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Every download by song id, finished or not. */
    val entries: StateFlow<Map<String, DownloadEntry>> = _entries.asStateFlow()

    /** Status by song id. Changes only when a download starts, finishes or fails (not on progress). */
    val status: StateFlow<Map<String, DownloadStatus>> = _status.asStateFlow()

    /** Finished downloads, newest first. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    /** 0..1 for songs downloading right now. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    val manager: DownloadManager = DownloadManager(
        app,
        app.databaseProvider,
        app.downloadCache,
        com.wavemoth.app.playback.AudioSources.forDownloads(app),
        Executors.newFixedThreadPool(MAX_PARALLEL),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL
        addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                loadIndex()
                if (downloadManager.currentDownloads.isNotEmpty()) startService()
            }

            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                update { it.apply { put(download) } }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                update { it.apply { remove(download.request.id) } }
            }
        })
    }

    /** Download [songs], skipping ones already saved or on the way. Failed ones are retried. */
    fun download(songs: List<Song>) {
        val current = _entries.value
        val todo = songs.distinctBy { it.id }.filter { s ->
            val st = current[s.id]?.status
            st == null || st == DownloadStatus.Failed
        }
        if (todo.isEmpty()) return
        val now = System.currentTimeMillis()
        todo.forEach { song ->
            manager.addDownload(
                DownloadRequest.Builder(song.id, songUri(song.id))
                    .setCustomCacheKey(songUri(song.id).toString())
                    .setData(song.toJson().toString().toByteArray())
                    .build(),
            )
            // Keep the cover too, so the song looks right offline.
            if (song.thumbnail.isNotBlank()) {
                app.imageLoader.enqueue(ImageRequest.Builder(app).data(song.thumbnail).build())
            }
        }
        // Show them as queued right away; the manager confirms a moment later.
        update { map -> map.apply { todo.forEach { put(it.id, DownloadEntry(it, DownloadStatus.Queued, now)) } } }
        startService()
    }

    /** Every song in the library: liked songs and all playlists. */
    fun librarySongs(library: LibraryRepository): List<Song> =
        (library.liked.value + library.playlists.value.flatMap { it.songs }).distinctBy { it.id }

    /** Downloads the whole library. Returns how many songs were added. */
    fun downloadLibrary(library: LibraryRepository): Int {
        val songs = librarySongs(library)
        val added = songs.count { _status.value[it.id].let { s -> s == null || s == DownloadStatus.Failed } }
        download(songs)
        return added
    }

    fun remove(songId: String) {
        manager.removeDownload(songId)
        update { it.apply { remove(songId) } }
    }

    fun removeAll() {
        manager.removeAllDownloads()
        update { it.apply { clear() } }
    }

    // ---- state ----

    private fun MutableMap<String, DownloadEntry>.put(download: Download) {
        val id = download.request.id
        val status = when (download.state) {
            Download.STATE_DOWNLOADING -> DownloadStatus.Downloading
            Download.STATE_COMPLETED -> DownloadStatus.Done
            Download.STATE_FAILED -> DownloadStatus.Failed
            Download.STATE_REMOVING -> { remove(id); return }
            else -> DownloadStatus.Queued  // queued, stopped, restarting
        }
        val song = this[id]?.song ?: songOf(download)
        this[id] = DownloadEntry(song, status, download.startTimeMs)
    }

    private fun songOf(download: Download): Song =
        runCatching { songFromJson(JSONObject(String(download.request.data))) }
            .getOrElse { Song(download.request.id, download.request.id, "", 0, "") }

    /** Applies a change on the main thread and republishes the derived lists. */
    private fun update(change: (MutableMap<String, DownloadEntry>) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { update(change) }
            return
        }
        val next = LinkedHashMap(_entries.value).also(change)
        _entries.value = next
        _status.value = next.mapValues { it.value.status }
        _songs.value = next.values.filter { it.status == DownloadStatus.Done }.sortedByDescending { it.addedAt }.map { it.song }
        if (next.values.any { it.status == DownloadStatus.Downloading }) startProgressUpdates() else _progress.value = emptyMap()
    }

    /** Finished downloads live in the manager's index, not in its list of current downloads. */
    private fun loadIndex() {
        Thread {
            val saved = runCatching {
                manager.downloadIndex.getDownloads().use { c ->
                    buildList { while (c.moveToNext()) add(c.download) }
                }
            }.getOrDefault(emptyList())
            update { map -> saved.forEach { d -> if (d.request.id !in map) map.put(d) } }
        }.start()
    }

    private var polling = false

    private fun startProgressUpdates() {
        if (polling) return
        polling = true
        main.post(object : Runnable {
            override fun run() {
                val active = manager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
                _progress.value = active.associate { it.request.id to (it.percentDownloaded / 100f).coerceIn(0f, 1f) }
                if (active.isEmpty()) polling = false else main.postDelayed(this, 500)
            }
        })
    }

    private fun startService() {
        // Fails only if the app is in the background; downloads then continue while the app is alive.
        runCatching { DownloadService.start(app, DownloadsService::class.java) }
    }

    private companion object {
        const val MAX_PARALLEL = 3
    }
}
