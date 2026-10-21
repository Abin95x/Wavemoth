package com.wavemoth.app.ui.browse

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Catalog
import com.wavemoth.app.data.Radio
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.YouTubeSource
import com.wavemoth.app.ui.collection.BackButton
import com.wavemoth.app.ui.collection.CollectionHeader
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.SectionLabel
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.pressClick
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

// ---------------------------------------------------------------- Artist

data class ArtistState(
    val songs: List<Song> = emptyList(),
    val albums: List<YouTubeSource.RemotePlaylist> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

class ArtistViewModel(private val name: String) : ViewModel() {
    private val _state = MutableStateFlow(ArtistState())
    val state: StateFlow<ArtistState> = _state

    init { load() }

    fun load() {
        _state.value = ArtistState()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val songs = async { Catalog.search(name).songs }
                    val albums = async { runCatching { Catalog.searchPlaylists(name, true) }.getOrDefault(emptyList()) }
                    val key = name.lowercase()
                    // Keep this artist's own songs and albums when the results are mixed.
                    val own = songs.await().filter { s -> Radio.artistsOf(s).any { it.contains(key) || key.contains(it) } }
                    val ownAlbums = albums.await().filter { it.owner.lowercase().contains(key) }
                    ArtistState(
                        songs = own.ifEmpty { songs.await() }.take(15),
                        albums = ownAlbums.ifEmpty { albums.await() }.take(12),
                        loading = false,
                    )
                }
            }
            _state.value = result.getOrElse { ArtistState(loading = false, error = true) }
        }
    }
}

@Composable
fun ArtistScreen(name: String, image: String, onOpenAlbum: (YouTubeSource.RemotePlaylist) -> Unit, onBack: () -> Unit) {
    val vm: ArtistViewModel = viewModel(key = "artist:$name") { ArtistViewModel(name) }
    val state by vm.state.collectAsState()
    val actions = LocalSongActions.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                CollectionHeader(
                    title = name,
                    subtitle = if (state.songs.isEmpty()) "" else "${state.songs.size} popular songs",
                    label = "Artist",
                    cover = if (image.isBlank()) null else { m -> Artwork(image, m, CircleShape) },
                    downloadSongs = state.songs,
                    canPlay = state.songs.isNotEmpty(),
                    onPlay = { actions.play(state.songs, 0, name) },
                    onShuffle = { actions.play(state.songs.shuffled(), 0, name) },
                )
            }
            when {
                state.loading -> item { Spinner() }
                state.error -> item { Retry("Couldn't load this artist.", vm::load) }
                else -> {
                    if (state.songs.isNotEmpty()) item { SectionLabel("Popular") }
                    itemsIndexed(state.songs, key = { _, s -> s.id }) { i, song ->
                        SongRow(
                            song,
                            onClick = { actions.play(state.songs, i, name) },
                            playing = actions.currentSongId() == song.id,
                            index = i,
                            onMore = { actions.showOptions(song, null) },
                        )
                    }
                    if (state.albums.isNotEmpty()) {
                        item { SectionLabel("Albums") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(state.albums, key = { it.url }) { album -> AlbumCard(album) { onOpenAlbum(album) } }
                            }
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

@Composable
private fun AlbumCard(album: YouTubeSource.RemotePlaylist, onClick: () -> Unit) {
    Column(Modifier.width(136.dp).pressClick(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Artwork(album.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).padding(bottom = 6.dp), RoundedCornerShape(10.dp))
        Text(album.name, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Album", style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
    }
}

// ---------------------------------------------------------------- Album / playlist from YouTube Music

data class RemotePlaylistState(
    val meta: YouTubeSource.RemotePlaylist? = null,
    val songs: List<Song> = emptyList(),
    val nextPage: Page? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val saving: Boolean = false,
    val savedId: String? = null,
)

class RemotePlaylistViewModel(private val url: String) : ViewModel() {
    private val _state = MutableStateFlow(RemotePlaylistState())
    val state: StateFlow<RemotePlaylistState> = _state

    init { load() }

    fun load() {
        _state.value = RemotePlaylistState()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Catalog.playlist(url) } }
            _state.value = result.fold(
                { RemotePlaylistState(meta = it.playlist, songs = it.songs, nextPage = it.nextPage, loading = false) },
                { RemotePlaylistState(loading = false, error = true) },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) { runCatching { Catalog.playlistMore(url, page) }.getOrNull() }
            _state.update { cur ->
                if (more == null) cur.copy(loadingMore = false)
                else cur.copy(songs = (cur.songs + more.songs).distinctBy { it.id }, nextPage = more.nextPage, loadingMore = false)
            }
        }
    }

    /** Copies the whole playlist (all pages, up to 500 songs) into Your Library. */
    fun save(onDone: (Boolean) -> Unit) {
        val s = _state.value
        val meta = s.meta ?: return
        if (s.saving || s.savedId != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) {
                runCatching {
                    var songs = s.songs
                    var page = s.nextPage
                    while (page != null && songs.size < 500) {
                        val more = Catalog.playlistMore(url, page)
                        songs = (songs + more.songs).distinctBy { it.id }
                        page = more.nextPage
                    }
                    songs
                }.getOrNull()
            }
            val id = all?.takeIf { it.isNotEmpty() }?.let { WavemothApp.instance.library.importPlaylist(meta.name, it).id }
            _state.update { it.copy(saving = false, savedId = id) }
            onDone(id != null)
        }
    }
}

@Composable
fun RemotePlaylistScreen(url: String, title: String, onBack: () -> Unit) {
    val vm: RemotePlaylistViewModel = viewModel(key = "remote:$url") { RemotePlaylistViewModel(url) }
    val state by vm.state.collectAsState()
    val actions = LocalSongActions.current
    val context = LocalContext.current
    val meta = state.meta
    val name = meta?.name ?: title
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.songs.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 4) vm.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                CollectionHeader(
                    title = name,
                    subtitle = buildString {
                        if (!meta?.owner.isNullOrBlank()) append("${meta?.owner} · ")
                        if (state.songs.isNotEmpty()) append("${state.songs.size}${if (state.nextPage != null) "+" else ""} songs")
                    }.removeSuffix(" · "),
                    label = if (meta?.isAlbum == true) "Album" else "Playlist",
                    cover = { m -> Artwork(meta?.thumbnail, m, RoundedCornerShape(14.dp)) },
                    downloadSongs = state.songs,
                    canPlay = state.songs.isNotEmpty(),
                    onPlay = { actions.play(state.songs, 0, name) },
                    onShuffle = { actions.play(state.songs.shuffled(), 0, name) },
                    actions = {
                        when {
                            state.saving -> CircularProgressIndicator(Modifier.padding(13.dp).size(22.dp), color = WavemothColors.Red, strokeWidth = 2.dp)
                            state.savedId != null -> Icon(Icons.Filled.CheckCircle, "Saved to Library", tint = WavemothColors.Red, modifier = Modifier.padding(12.dp))
                            else -> IconButton(onClick = {
                                vm.save { ok -> Toast.makeText(context, if (ok) "Saved to Library" else "Couldn't save this playlist", Toast.LENGTH_SHORT).show() }
                            }, enabled = state.songs.isNotEmpty()) {
                                Icon(Icons.Filled.LibraryAdd, "Save to Library", tint = WavemothColors.TextSecondary)
                            }
                        }
                    },
                )
            }
            when {
                state.loading -> item { Spinner() }
                state.error -> item { Retry("Couldn't load this playlist.", vm::load) }
            }
            itemsIndexed(state.songs, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song,
                    onClick = { actions.play(state.songs, i, name) },
                    playing = actions.currentSongId() == song.id,
                    onMore = { actions.showOptions(song, null) },
                )
            }
            if (state.loadingMore) item { Spinner() }
        }
        BackButton(onBack)
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = WavemothColors.Red, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun Retry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = WavemothColors.TextSecondary)
        TextButton(onClick = onRetry) { Text("Try again", color = WavemothColors.Red) }
    }
}
