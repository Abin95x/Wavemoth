package com.wavemoth.app.ui.collection

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.formatDuration
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.LikedSongsCover
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.NameDialog
import com.wavemoth.app.ui.components.RecentCover
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.rememberFileSaver
import com.wavemoth.app.ui.components.safeFileName
import com.wavemoth.app.data.PlaylistFile
import androidx.compose.material.icons.filled.SaveAlt
import com.wavemoth.app.ui.components.rememberArtworkColor
import com.wavemoth.app.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.wavemoth.app.ui.search.SearchViewModel
import com.wavemoth.app.ui.search.SongResults
import com.wavemoth.app.ui.theme.WavemothColors

/** Liked Songs. */
@Composable
fun LikedScreen(onBack: () -> Unit) {
    val songs by WavemothApp.instance.library.liked.collectAsState()
    val saveFile = rememberFileSaver("text/csv")
    SongCollection(
        title = "Liked Songs",
        songs = songs,
        headerColor = Color(0xFF5038A0),
        cover = { LikedSongsCover(it) },
        emptyText = "Songs you like will appear here. Tap the heart on any song.",
        onBack = onBack,
        menu = { close ->
            DropdownMenuItem(
                text = { Text("Export as file") },
                leadingIcon = { Icon(Icons.Filled.SaveAlt, null) },
                enabled = songs.isNotEmpty(),
                onClick = { close(); saveFile("Liked Songs.csv") { PlaylistFile.fromSongs(songs) } },
            )
        },
    )
}

/** Recently played. */
@Composable
fun RecentScreen(onBack: () -> Unit) {
    val songs by WavemothApp.instance.library.recent.collectAsState()
    SongCollection(
        title = "Recently played",
        songs = songs,
        headerColor = Color(0xFF0D7347),
        cover = { RecentCover(it) },
        emptyText = "Nothing played yet.",
        onBack = onBack,
    )
}

/** A user playlist, with rename / delete. */
@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit) {
    val library = WavemothApp.instance.library
    val playlist by remember(playlistId) { library.playlist(playlistId) }.collectAsState(initial = null)
    var renaming by remember { mutableStateOf(false) }
    val saveFile = rememberFileSaver("text/csv")
    val p = playlist ?: run {
        Box(Modifier.fillMaxSize())
        return
    }
    val art = p.songs.firstOrNull()?.thumbnail
    SongCollection(
        title = p.name,
        songs = p.songs,
        headerColor = rememberArtworkColor(art, Color(0xFF535353)),
        cover = { Artwork(art, it) },
        emptyText = "This playlist is empty. Add songs from the ⋮ menu on any song.",
        playlistId = p.id,
        onBack = onBack,
        menu = { close ->
            DropdownMenuItem(
                text = { Text("Export as file") },
                leadingIcon = { Icon(Icons.Filled.SaveAlt, null) },
                enabled = p.songs.isNotEmpty(),
                onClick = { close(); saveFile(safeFileName(p.name, "csv")) { PlaylistFile.fromSongs(p.songs) } },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { close(); renaming = true },
            )
            DropdownMenuItem(
                text = { Text("Delete playlist") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { close(); library.deletePlaylist(p.id); onBack() },
            )
        },
    )
    NameDialog(visible = renaming, title = "Rename playlist", initial = p.name, confirmLabel = "Save", onDismiss = { renaming = false }) {
        library.renamePlaylist(p.id, it)
        renaming = false
    }
}

/** Genre / "show all" / artist page, filled from a search. */
@Composable
fun CategoryScreen(title: String, query: String, colorArgb: Long, onBack: () -> Unit) {
    val vm: SearchViewModel = viewModel(key = "category:$query") { SearchViewModel(query) }
    val state by vm.state.collectAsState()
    val actions = LocalSongActions.current
    Box(Modifier.fillMaxSize()) {
        SongResults(state, title, onLoadMore = vm::loadMore, onRetry = vm::retry, header = {
            CollectionHeader(
                title = title,
                subtitle = if (state.songs.isEmpty()) "" else "${state.songs.size} songs",
                headerColor = Color(colorArgb.toInt()),
                cover = null,
                canPlay = state.songs.isNotEmpty(),
                onPlay = { actions.play(state.songs, 0, title) },
                onShuffle = { actions.play(state.songs.shuffled(), 0, title) },
            )
        })
        BackButton(onBack)
    }
}

@Composable
private fun SongCollection(
    title: String,
    songs: List<Song>,
    headerColor: Color,
    cover: @Composable (Modifier) -> Unit,
    emptyText: String,
    onBack: () -> Unit,
    playlistId: String? = null,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    val actions = LocalSongActions.current
    val totalSec = songs.sumOf { it.durationSec }
    val subtitle = buildString {
        append("${songs.size} songs")
        if (totalSec > 0) append(" • ${totalSec.formatDuration()}")
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                CollectionHeader(
                    title, subtitle, headerColor, cover,
                    canPlay = songs.isNotEmpty(),
                    onPlay = { actions.play(songs, 0, title) },
                    onShuffle = { actions.play(songs.shuffled(), 0, title) },
                    menu = menu,
                )
            }
            if (songs.isEmpty()) {
                item { Text(emptyText, color = WavemothColors.TextSecondary, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                SongRow(
                    song,
                    onClick = { actions.play(songs, i, title) },
                    playing = actions.currentSongId() == song.id,
                    onMore = { actions.showOptions(song, playlistId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        BackButton(onBack)
    }
}

@Composable
internal fun CollectionHeader(
    title: String,
    subtitle: String,
    headerColor: Color,
    cover: (@Composable (Modifier) -> Unit)?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(headerColor, WavemothColors.Background)))
            .statusBarsPadding()
            .padding(top = 56.dp, start = 16.dp, end = 16.dp),
    ) {
        if (cover != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                cover(Modifier.size(220.dp))
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Spacer(Modifier.height(64.dp))
        }
        Text(
            title,
            style = if (cover == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (menu != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = WavemothColors.TextSecondary) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
                }
            }
            actions()
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShuffle, enabled = canPlay) {
                Icon(Icons.Filled.Shuffle, "Shuffle play", tint = WavemothColors.TextSecondary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(4.dp))
            val playPress = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = onPlay,
                enabled = canPlay,
                interactionSource = playPress,
                modifier = Modifier.size(56.dp).pressScale(playPress),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = WavemothColors.Green, contentColor = Color.Black),
            ) {
                Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.statusBarsPadding().padding(8.dp).size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
    }
}

