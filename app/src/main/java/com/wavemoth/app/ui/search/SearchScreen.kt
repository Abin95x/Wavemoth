package com.wavemoth.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.Catalog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import com.wavemoth.app.data.YouTubeSource
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.pressClick
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

data class Category(val title: String, val query: String, val color: Color)

val browseCategories = listOf(
    Category("Pop", "pop hits", Color(0xFF8D67AB)),
    Category("Hip-Hop", "hip hop hits", Color(0xFFBA5D07)),
    Category("Rock", "rock classics", Color(0xFFE91429)),
    Category("Chill", "chill songs", Color(0xFF477D95)),
    Category("Workout", "workout songs", Color(0xFF777777)),
    Category("Focus", "focus music instrumental", Color(0xFF503750)),
    Category("Party", "party songs", Color(0xFFAF2896)),
    Category("Love", "romantic songs", Color(0xFFDC148C)),
    Category("Indie", "indie songs", Color(0xFF608108)),
    Category("Electronic", "electronic dance music", Color(0xFF0D73EC)),
    Category("Jazz", "jazz classics", Color(0xFF1E3264)),
    Category("Classical", "classical music", Color(0xFF7D4B32)),
    Category("K-Pop", "kpop hits", Color(0xFFE8115B)),
    Category("Latin", "latin hits", Color(0xFFE1118C)),
    Category("Bollywood", "bollywood hits", Color(0xFFD84000)),
    Category("Malayalam", "malayalam hit songs", Color(0xFF148A08)),
    Category("Tamil", "tamil hit songs", Color(0xFF8C1932)),
    Category("Sleep", "sleep music", Color(0xFF1E3264)),
)

enum class SearchFilter(val label: String) { Songs("Songs"), Artists("Artists"), Albums("Albums"), Playlists("Playlists") }

data class SearchState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.Songs,
    val songs: List<Song> = emptyList(),
    val artists: List<YouTubeSource.Artist> = emptyList(),
    val playlists: List<YouTubeSource.RemotePlaylist> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Page? = null,
)

/** Search songs, artists, albums or playlists; also reused (with a fixed query) for genre pages. */
@OptIn(FlowPreview::class)
class SearchViewModel(initialQuery: String = "") : ViewModel() {
    private val _state = MutableStateFlow(SearchState(query = initialQuery))
    val state: StateFlow<SearchState> = _state
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.debounce(300)
                .distinctUntilChanged { a, b -> a.query == b.query && a.filter == b.filter }
                .collect { run(it.query, it.filter) }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun onFilterChange(filter: SearchFilter) = _state.update { it.copy(filter = filter) }

    fun retry() = run(_state.value.query, _state.value.filter)

    private fun run(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(songs = emptyList(), artists = emptyList(), playlists = emptyList(), loading = false, error = null, nextPage = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (filter) {
                        SearchFilter.Songs -> Catalog.search(query).let { SearchState(songs = it.songs, nextPage = it.nextPage) }
                        SearchFilter.Artists -> SearchState(artists = Catalog.searchArtists(query))
                        SearchFilter.Albums -> SearchState(playlists = Catalog.searchPlaylists(query, true))
                        SearchFilter.Playlists -> SearchState(playlists = Catalog.searchPlaylists(query, false))
                    }
                }
            }
            _state.update { s ->
                result.fold(
                    { r -> s.copy(songs = r.songs, artists = r.artists, playlists = r.playlists, nextPage = r.nextPage, loading = false) },
                    { s.copy(loading = false, error = "Couldn't search right now. Check your connection.") },
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore || s.loading || s.filter != SearchFilter.Songs) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Catalog.searchMore(s.query, page) } }
            _state.update { cur ->
                val more = result.getOrNull()
                if (more == null || cur.query != s.query) cur.copy(loadingMore = false)
                else cur.copy(songs = (cur.songs + more.songs).distinctBy { it.id }, nextPage = more.nextPage, loadingMore = false)
            }
        }
    }
}

@Composable
fun SearchScreen(
    onOpenCategory: (Category) -> Unit,
    onOpenArtist: (YouTubeSource.Artist) -> Unit,
    onOpenPlaylist: (YouTubeSource.RemotePlaylist) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        SearchBar(state.query, vm::onQueryChange, onSubmit = { focus.clearFocus() })
        Spacer(Modifier.height(8.dp))

        if (state.query.isBlank()) {
            BrowseGrid(onOpenCategory)
        } else {
            FilterChips(state.filter, vm::onFilterChange)
            when (state.filter) {
                SearchFilter.Songs -> SongResults(state, "Search", onLoadMore = vm::loadMore, onRetry = vm::retry)
                SearchFilter.Artists -> ResultList(state, vm::retry, state.artists, { it.url }) { artist ->
                    ResultRow(
                        title = artist.name,
                        subtitle = "Artist" + if (artist.subscribers > 0) " • ${compactCount(artist.subscribers)} followers" else "",
                        image = artist.thumbnail,
                        round = true,
                        onClick = { onOpenArtist(artist) },
                    )
                }
                SearchFilter.Albums, SearchFilter.Playlists -> ResultList(state, vm::retry, state.playlists, { it.url }) { p ->
                    ResultRow(
                        title = p.name,
                        subtitle = (if (p.isAlbum) "Album" else "Playlist") + if (p.owner.isNotBlank()) " • ${p.owner}" else "",
                        image = p.thumbnail,
                        round = false,
                        onClick = { onOpenPlaylist(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SearchFilter.entries) { f ->
            val active = f == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) WavemothColors.Green else WavemothColors.Elevated)
                    .selectable(selected = active, role = Role.Tab) { onSelect(f) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(f.label, style = MaterialTheme.typography.titleSmall, color = if (active) Color.Black else Color.White)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T> ResultList(state: SearchState, onRetry: () -> Unit, items: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when {
            state.loading -> item { CenteredSpinner() }
            state.error != null -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = WavemothColors.TextSecondary)
                    TextButton(onClick = onRetry) { Text("Try again", color = WavemothColors.Green) }
                }
            }
            items.isEmpty() -> item {
                Text("No ${state.filter.label.lowercase()} found for \"${state.query}\"", color = WavemothColors.TextSecondary, modifier = Modifier.padding(16.dp))
            }
            else -> items(items, key = key) { row(it) }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, image: String, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(image.ifBlank { null }, Modifier.size(56.dp), if (round) CircleShape else RoundedCornerShape(4.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 111000000 → "111M", 65800 → "65.8K". */
fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> trimZero(n / 1e9) + "B"
    n >= 1_000_000 -> trimZero(n / 1e6) + "M"
    n >= 1_000 -> trimZero(n / 1e3) + "K"
    else -> n.toString()
}

private fun trimZero(v: Double): String = if (v >= 100 || v % 1.0 < 0.05) "%.0f".format(v) else "%.1f".format(v)

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Color(0xFF121212), modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("What do you want to listen to?", color = Color(0xFF535353), style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF121212)),
                cursorBrush = SolidColor(Color(0xFF121212)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear", tint = Color(0xFF121212)) }
        }
    }
}

@Composable
private fun BrowseGrid(onOpenCategory: (Category) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Browse all", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp)) }
        browseCategories.chunked(2).forEach { pair ->
            item(key = pair.first().title) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { CategoryTile(it, Modifier.weight(1f)) { onOpenCategory(it) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(100.dp).pressClick(onClick = onClick).clip(RoundedCornerShape(6.dp)).background(category.color)) {
        Text(category.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
        // Tilted accent block in the corner.
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 8.dp).size(64.dp).rotate(25f)
                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.25f)),
        )
    }
}

/** A song list with loading, error and load-more handling. */
@Composable
fun SongResults(
    state: SearchState,
    source: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val actions = LocalSongActions.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.songs.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 4) onLoadMore() }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        header?.let { item { it() } }
        when {
            state.loading -> item { CenteredSpinner() }
            state.error != null -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = WavemothColors.TextSecondary)
                    TextButton(onClick = onRetry) { Text("Try again", color = WavemothColors.Green) }
                }
            }
            state.songs.isEmpty() && state.query.isNotBlank() -> item {
                Text("No songs found for \"${state.query}\"", color = WavemothColors.TextSecondary, modifier = Modifier.padding(16.dp))
            }
        }
        itemsIndexed(state.songs, key = { _, s -> s.id }) { i, song ->
            SongRow(
                song,
                onClick = { actions.play(state.songs, i, source) },
                playing = actions.currentSongId() == song.id,
                onMore = { actions.showOptions(song, null) },
            )
        }
        if (state.loadingMore) item { CenteredSpinner(Modifier.padding(8.dp)) }
    }
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = WavemothColors.Green, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
    }
}

