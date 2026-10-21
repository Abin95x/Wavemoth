package com.wavemoth.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.wavemoth.app.ui.components.Motion
import com.wavemoth.app.ui.components.SectionLabel
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page

data class Category(val title: String, val query: String)

val browseCategories = listOf(
    Category("Pop", "pop hits"),
    Category("Hip-Hop", "hip hop hits"),
    Category("Rock", "rock classics"),
    Category("Chill", "chill songs"),
    Category("Workout", "workout songs"),
    Category("Focus", "focus music instrumental"),
    Category("Party", "party songs"),
    Category("Love", "romantic songs"),
    Category("Indie", "indie songs"),
    Category("Electronic", "electronic dance music"),
    Category("Jazz", "jazz classics"),
    Category("Classical", "classical music"),
    Category("K-Pop", "kpop hits"),
    Category("Latin", "latin hits"),
    Category("Bollywood", "bollywood hits"),
    Category("Malayalam", "malayalam hit songs"),
    Category("Tamil", "tamil hit songs"),
    Category("Sleep", "sleep music"),
)

enum class SearchFilter(val label: String) { All("All"), Songs("Songs"), Artists("Artists"), Albums("Albums"), Playlists("Playlists") }

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
class SearchViewModel(initialQuery: String = "", initialFilter: SearchFilter = SearchFilter.All) : ViewModel() {
    private val _state = MutableStateFlow(SearchState(query = initialQuery, filter = initialFilter))
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
                        SearchFilter.All -> searchAll(query)
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

    /** Every kind at once. Fails only if every one of the four searches fails. */
    private suspend fun searchAll(query: String): SearchState = coroutineScope {
        val songs = async { runCatching { Catalog.search(query) } }
        val artists = async { runCatching { Catalog.searchArtists(query) } }
        val albums = async { runCatching { Catalog.searchPlaylists(query, true) } }
        val playlists = async { runCatching { Catalog.searchPlaylists(query, false) } }
        val results = listOf(songs, artists, albums, playlists).awaitAll()
        results.firstOrNull { it.isSuccess } ?: throw results.first().exceptionOrNull()!!
        SearchState(
            songs = songs.await().getOrNull()?.songs.orEmpty(),
            artists = artists.await().getOrNull().orEmpty(),
            playlists = (albums.await().getOrNull().orEmpty() + playlists.await().getOrNull().orEmpty()).distinctBy { it.url },
        )
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
        Text("Search", style = MaterialTheme.typography.headlineMedium, color = WavemothColors.TextPrimary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 16.dp))
        SearchBar(state.query, vm::onQueryChange, onSubmit = { focus.clearFocus() })
        Spacer(Modifier.height(4.dp))

        AnimatedContent(
            targetState = state.query.isBlank(),
            transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "searchBody",
        ) { blank ->
            if (blank) {
                BrowseGrid(onOpenCategory)
            } else Column {
                FilterTabs(state.filter, vm::onFilterChange)
                when (state.filter) {
                    SearchFilter.All -> AllResults(state, vm::retry, vm::onFilterChange, onOpenArtist, onOpenPlaylist)
                    SearchFilter.Songs -> SongResults(state, "Search", onLoadMore = vm::loadMore, onRetry = vm::retry)
                    SearchFilter.Artists -> ResultList(state, vm::retry, state.artists, { it.url }) { ArtistResult(it, onOpenArtist) }
                    SearchFilter.Albums, SearchFilter.Playlists -> ResultList(state, vm::retry, state.playlists, { it.url }) { PlaylistResult(it, onOpenPlaylist) }
                }
            }
        }
    }
}

/** Text tabs with a red line under the selected one that slides between them. */
@Composable
private fun FilterTabs(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    val bounds = remember { mutableStateMapOf<SearchFilter, Pair<Float, Float>>() }
    val target = bounds[selected]
    val left by animateFloatAsState(target?.first ?: 0f, tween(Motion.MEDIUM, easing = Motion.Emphasized), label = "tabLeft")
    val width by animateFloatAsState(target?.second ?: 0f, tween(Motion.MEDIUM, easing = Motion.Emphasized), label = "tabWidth")
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(WavemothColors.Hairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                if (width > 0f) drawLine(WavemothColors.Red, Offset(left, y), Offset(left + width, y), 2.dp.toPx())
            },
    ) {
        SearchFilter.entries.forEach { f ->
            val active = f == selected
            val color by animateColorAsState(if (active) WavemothColors.TextPrimary else WavemothColors.TextSecondary, tween(Motion.MEDIUM), label = "tabColor")
            Box(
                Modifier
                    .onGloballyPositioned { bounds[f] = it.positionInParent().x to it.size.width.toFloat() }
                    .selectable(selected = active, role = Role.Tab) { onSelect(f) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(f.label, style = MaterialTheme.typography.titleSmall, color = color)
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
            state.error != null -> item { ErrorMessage(state.error, onRetry) }
            items.isEmpty() -> item {
                Text("No ${state.filter.label.lowercase()} found for \"${state.query}\"", color = WavemothColors.TextSecondary, modifier = Modifier.padding(20.dp))
            }
            else -> items(items, key = key) { row(it) }
        }
    }
}

/** The "All" tab: a few of each kind, each with "See all" to open its own tab. */
@Composable
private fun AllResults(
    state: SearchState,
    onRetry: () -> Unit,
    onSeeAll: (SearchFilter) -> Unit,
    onOpenArtist: (YouTubeSource.Artist) -> Unit,
    onOpenPlaylist: (YouTubeSource.RemotePlaylist) -> Unit,
) {
    val actions = LocalSongActions.current
    val albums = state.playlists.filter { it.isAlbum }
    val playlists = state.playlists.filterNot { it.isAlbum }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        when {
            state.loading -> item { CenteredSpinner() }
            state.error != null -> item { ErrorMessage(state.error, onRetry) }
            state.songs.isEmpty() && state.artists.isEmpty() && state.playlists.isEmpty() -> item {
                Text("Nothing found for \"${state.query}\"", color = WavemothColors.TextSecondary, modifier = Modifier.padding(20.dp))
            }
            else -> {
                if (state.artists.isNotEmpty()) {
                    item(key = "label:artists") { AllSectionLabel(SearchFilter.Artists, onSeeAll) }
                    items(state.artists.take(3), key = { "artist:" + it.url }) { ArtistResult(it, onOpenArtist) }
                }
                if (state.songs.isNotEmpty()) {
                    item(key = "label:songs") { AllSectionLabel(SearchFilter.Songs, onSeeAll) }
                    itemsIndexed(state.songs.take(6), key = { _, s -> "song:" + s.id }) { i, song ->
                        SongRow(
                            song,
                            onClick = { actions.play(state.songs, i, "Search") },
                            playing = actions.currentSongId() == song.id,
                            onMore = { actions.showOptions(song, null) },
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "label:albums") { AllSectionLabel(SearchFilter.Albums, onSeeAll) }
                    items(albums.take(4), key = { "album:" + it.url }) { PlaylistResult(it, onOpenPlaylist) }
                }
                if (playlists.isNotEmpty()) {
                    item(key = "label:playlists") { AllSectionLabel(SearchFilter.Playlists, onSeeAll) }
                    items(playlists.take(4), key = { "playlist:" + it.url }) { PlaylistResult(it, onOpenPlaylist) }
                }
            }
        }
    }
}

@Composable
private fun AllSectionLabel(filter: SearchFilter, onSeeAll: (SearchFilter) -> Unit) {
    SectionLabel(filter.label, Modifier.padding(bottom = 0.dp)) {
        Text(
            "See all",
            style = MaterialTheme.typography.labelMedium,
            color = WavemothColors.Red,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSeeAll(filter) }.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ArtistResult(artist: YouTubeSource.Artist, onOpen: (YouTubeSource.Artist) -> Unit) = ResultRow(
    title = artist.name,
    subtitle = "Artist" + if (artist.subscribers > 0) " · ${compactCount(artist.subscribers)} followers" else "",
    image = artist.thumbnail,
    round = true,
    onClick = { onOpen(artist) },
)

@Composable
private fun PlaylistResult(p: YouTubeSource.RemotePlaylist, onOpen: (YouTubeSource.RemotePlaylist) -> Unit) = ResultRow(
    title = p.name,
    subtitle = (if (p.isAlbum) "Album" else "Playlist") + if (p.owner.isNotBlank()) " · ${p.owner}" else "",
    image = p.thumbnail,
    round = false,
    onClick = { onOpen(p) },
)

@Composable
private fun ResultRow(title: String, subtitle: String, image: String, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(image.ifBlank { null }, Modifier.size(52.dp), if (round) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) WavemothColors.Red else WavemothColors.Hairline, tween(Motion.MEDIUM), label = "searchBorder")
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp)
            .clip(shape).background(WavemothColors.Surface).border(1.dp, border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = if (focused) WavemothColors.Red else WavemothColors.TextSecondary, modifier = Modifier.padding(horizontal = 14.dp).size(22.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Songs, artists, albums", color = WavemothColors.TextTertiary, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = WavemothColors.TextPrimary),
                cursorBrush = SolidColor(WavemothColors.Red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear", tint = WavemothColors.TextSecondary) }
        }
    }
}

@Composable
private fun BrowseGrid(onOpenCategory: (Category) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionLabel("Browse", Modifier.padding(bottom = 0.dp)) }
        browseCategories.withIndex().chunked(2).forEach { pair ->
            item(key = pair.first().value.title) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (i, c) -> CategoryTile(i, c, Modifier.weight(1f)) { onOpenCategory(c) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(index: Int, category: Category, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(60.dp).pressClick(onClick = onClick)
            .clip(shape).background(WavemothColors.Surface).border(1.dp, WavemothColors.Hairline, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), style = MaterialTheme.typography.labelMedium, color = WavemothColors.Red)
        Spacer(Modifier.width(12.dp))
        Text(category.title, style = MaterialTheme.typography.titleMedium, color = WavemothColors.TextPrimary, maxLines = 1)
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
            state.error != null -> item { ErrorMessage(state.error, onRetry) }
            state.songs.isEmpty() && state.query.isNotBlank() -> item {
                Text("No songs found for \"${state.query}\"", color = WavemothColors.TextSecondary, modifier = Modifier.padding(20.dp))
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
        CircularProgressIndicator(color = WavemothColors.Red, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = WavemothColors.TextSecondary)
        TextButton(onClick = onRetry) { Text("Try again", color = WavemothColors.Red) }
    }
}

