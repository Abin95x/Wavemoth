package com.wavemoth.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Liked songs, playlists and listening history, stored on the device as one JSON file. */
class LibraryRepository(private val file: File) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called with a song id whenever it is liked or unliked. */
    var onLiked: ((String) -> Unit)? = null
    private val writeLock = Mutex()

    private val _liked = MutableStateFlow<List<Song>>(emptyList())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _recent = MutableStateFlow<List<Song>>(emptyList())

    val liked: StateFlow<List<Song>> = _liked.asStateFlow()
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    val recent: StateFlow<List<Song>> = _recent.asStateFlow()

    init {
        load()
    }

    fun playlist(id: String): Flow<Playlist?> = playlists.map { all -> all.firstOrNull { it.id == id } }

    fun toggleLike(song: Song) {
        onLiked?.invoke(song.id)
        _liked.update { list -> if (list.any { it.id == song.id }) list.filterNot { it.id == song.id } else listOf(song) + list }
        save()
    }

    /** Likes imported songs; ones already liked are left where they are. Returns how many were new. */
    fun addLiked(songs: List<Song>): Int {
        var added = emptyList<Song>()
        _liked.update { cur ->
            added = songs.distinctBy { it.id }.filterNot { s -> cur.any { it.id == s.id } }
            added + cur
        }
        added.forEach { onLiked?.invoke(it.id) }
        save()
        return added.size
    }

    /** Creates a playlist from imported songs. */
    fun importPlaylist(name: String, songs: List<Song>): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "Imported playlist" }, songs, System.currentTimeMillis())
        _playlists.update { listOf(playlist) + it }
        save()
        return playlist
    }

    fun addRecent(song: Song) {
        _recent.update { list -> (listOf(song) + list.filterNot { it.id == song.id }).take(MAX_RECENT) }
        save()
    }

    fun createPlaylist(name: String, firstSong: Song? = null): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "My playlist" }, listOfNotNull(firstSong), System.currentTimeMillis())
        _playlists.update { listOf(playlist) + it }
        save()
        return playlist
    }

    fun addToPlaylist(playlistId: String, song: Song) = editPlaylist(playlistId) {
        if (it.songs.any { s -> s.id == song.id }) it else it.copy(songs = it.songs + song)
    }

    fun removeFromPlaylist(playlistId: String, songId: String) = editPlaylist(playlistId) {
        it.copy(songs = it.songs.filterNot { s -> s.id == songId })
    }

    fun renamePlaylist(playlistId: String, name: String) = editPlaylist(playlistId) {
        it.copy(name = name.trim().ifBlank { it.name })
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.update { list -> list.filterNot { it.id == playlistId } }
        save()
    }

    private fun editPlaylist(id: String, change: (Playlist) -> Playlist) {
        _playlists.update { list -> list.map { if (it.id == id) change(it) else it } }
        save()
    }

    // ---- persistence ----

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            _liked.value = root.optJSONArray("liked").toSongs()
            _recent.value = root.optJSONArray("recent").toSongs()
            val lists = root.optJSONArray("playlists") ?: JSONArray()
            _playlists.value = (0 until lists.length()).map { i ->
                val p = lists.getJSONObject(i)
                Playlist(p.getString("id"), p.getString("name"), p.optJSONArray("songs").toSongs(), p.optLong("createdAt"))
            }
        }
    }

    /** Everything in the library as one JSON document (for backups). */
    fun exportBackup(): String = toJson().put("app", "wavemoth").put("version", 1).toString(2)

    /**
     * Merges a backup into the library: adds missing liked songs and history, adds playlists
     * (a playlist with the same id is replaced). Returns how many playlists were restored.
     */
    fun restoreBackup(text: String): Int {
        val root = JSONObject(text)
        require(root.has("playlists") || root.has("liked")) { "Not a Wavemoth backup file" }
        val liked = root.optJSONArray("liked").toSongs()
        val recent = root.optJSONArray("recent").toSongs()
        val lists = root.optJSONArray("playlists") ?: JSONArray()
        val restored = (0 until lists.length()).map { i ->
            val p = lists.getJSONObject(i)
            Playlist(p.getString("id"), p.getString("name"), p.optJSONArray("songs").toSongs(), p.optLong("createdAt"))
        }
        _liked.update { cur -> cur + liked.filterNot { s -> cur.any { it.id == s.id } } }
        _recent.update { cur -> (cur + recent.filterNot { s -> cur.any { it.id == s.id } }).take(MAX_RECENT) }
        _playlists.update { cur -> restored + cur.filterNot { c -> restored.any { it.id == c.id } } }
        save()
        return restored.size
    }

    private fun toJson(): JSONObject = JSONObject()
        .put("liked", _liked.value.toJson())
        .put("recent", _recent.value.toJson())
        .put("playlists", JSONArray(_playlists.value.map {
            JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt).put("songs", it.songs.toJson())
        }))

    private fun save() {
        val json = toJson().toString()
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(file)
            }
        }
    }

    private fun List<Song>.toJson() = JSONArray(map { it.toJson() })

    private fun JSONArray?.toSongs(): List<Song> {
        if (this == null) return emptyList()
        return (0 until length()).map { i -> songFromJson(getJSONObject(i)) }
    }

    private companion object {
        const val MAX_RECENT = 50
    }
}
