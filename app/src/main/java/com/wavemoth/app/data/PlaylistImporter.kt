package com.wavemoth.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/** Progress of a playlist-file import; null in the flow when nothing is running or to report. */
data class ImportStatus(
    val name: String,
    val done: Int,
    val total: Int,
    val found: Int,
    val finished: Boolean = false,
    val playlistId: String? = null,
    val error: String? = null,
    /** True when the songs went into Liked Songs instead of a new playlist. */
    val intoLiked: Boolean = false,
)

/**
 * Turns a playlist file into a Wavemoth playlist (or adds it to Liked Songs). Rows that already have a YouTube id are added
 * directly; the rest are looked up on YouTube Music. Runs app-wide so leaving the screen doesn't stop it.
 */
class PlaylistImporter(private val library: LibraryRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _status = MutableStateFlow<ImportStatus?>(null)
    val status: StateFlow<ImportStatus?> = _status.asStateFlow()

    fun import(name: String, csv: String, intoLiked: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            val rows = runCatching { PlaylistFile.parse(csv) }.getOrDefault(emptyList())
            if (rows.isEmpty()) {
                _status.value = ImportStatus(name, 0, 0, 0, finished = true, error = "No songs found in that file. It needs a header row with a \"title\" column.", intoLiked = intoLiked)
                return@launch
            }
            val done = AtomicInteger()
            val found = AtomicInteger()
            _status.value = ImportStatus(name, 0, rows.size, 0, intoLiked = intoLiked)
            val gate = Semaphore(3)  // a few searches at a time so YouTube doesn't block us
            val songs = rows.map { row ->
                async {
                    val song = row.toSongOrNull() ?: gate.withPermit { SongMatcher.find(row) }
                    if (song != null) found.incrementAndGet()
                    _status.value = ImportStatus(name, done.incrementAndGet(), rows.size, found.get(), intoLiked = intoLiked)
                    song
                }
            }.awaitAll().filterNotNull().distinctBy { it.id }
            if (songs.isEmpty()) {
                _status.value = ImportStatus(name, rows.size, rows.size, 0, finished = true, error = "None of the songs in that file could be found.", intoLiked = intoLiked)
                return@launch
            }
            if (intoLiked) {
                library.addLiked(songs)
                _status.value = ImportStatus(name, rows.size, rows.size, songs.size, finished = true, intoLiked = true)
            } else {
                val playlist = library.importPlaylist(name, songs)
                _status.value = ImportStatus(name, rows.size, rows.size, songs.size, finished = true, playlistId = playlist.id)
            }
        }
    }

    fun dismiss() {
        if (_status.value?.finished == true) _status.value = null
    }
}
