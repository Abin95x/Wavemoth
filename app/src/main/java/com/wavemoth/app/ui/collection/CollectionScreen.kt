@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wavemoth.app.ui.collection

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.PlaylistFile
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.formatDuration
import com.wavemoth.app.download.DownloadStatus
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.ConfirmDialog
import com.wavemoth.app.ui.components.DownloadAllButton
import com.wavemoth.app.ui.components.DownloadsCover
import com.wavemoth.app.ui.components.IconCover
import com.wavemoth.app.ui.components.LikedSongsCover
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.NameDialog
import com.wavemoth.app.ui.components.PillButton
import com.wavemoth.app.ui.components.RecentCover
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.rememberFileSaver
import com.wavemoth.app.ui.components.safeFileName
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
        label = "Collection",
        songs = songs,
        cover = { LikedSongsCover(it, RoundedCornerShape(14.dp)) },
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
        label = "History",
        songs = songs,
        cover = { RecentCover(it, RoundedCornerShape(14.dp)) },
        emptyText = "Nothing played yet.",
        onBack = onBack,
    )
}

/** Songs saved on the phone, plus a way to download the whole library at once. */
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val app = WavemothApp.instance
    val downloads = app.downloads
    val songs by downloads.songs.collectAsState()
    val entries by downloads.entries.collectAsState()
    val liked by app.library.liked.collectAsState()
    val playlists by app.library.playlists.collectAsState()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }

    val librarySongs = remember(liked, playlists) { downloads.librarySongs(app.library) }
    val missing = librarySongs.count { entries[it.id] == null }
    val active = entries.values.count { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
    val failed = entries.values.filter { it.status == DownloadStatus.Failed }.map { it.song }

    SongCollection(
        title = "Downloads",
        label = "On this phone",
        songs = songs,
        cover = { DownloadsCover(it, RoundedCornerShape(14.dp)) },
        emptyText = "Downloaded songs play without internet.",
        onBack = onBack,
        showDownloadButton = false,
        menu = { close ->
            DropdownMenuItem(
                text = { Text("Remove all downloads") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                enabled = entries.isNotEmpty(),
                onClick = { close(); confirmRemove = true },
            )
        },
        extra = {
            if (active > 0) item {
                val total = entries.size
                val done = total - active - failed.size
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Downloading · $done of $total", style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                        color = WavemothColors.Red,
                        trackColor = WavemothColors.Hairline,
                        drawStopIndicator = {},
                    )
                }
            }
            if (missing > 0) item {
                ActionRow(
                    icon = Icons.Filled.Download,
                    title = "Download all songs",
                    subtitle = "$missing songs from Liked Songs and your playlists",
                    onClick = {
                        val n = downloads.downloadLibrary(app.library)
                        Toast.makeText(context, "Downloading $n songs", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            if (failed.isNotEmpty()) item {
                ActionRow(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "${failed.size} couldn't download",
                    subtitle = "Tap to try again",
                    onClick = { downloads.download(failed) },
                )
            }
        },
    )
    ConfirmDialog(
        visible = confirmRemove,
        title = "Remove all downloads?",
        message = "Songs stay in your library and will stream again.",
        confirmLabel = "Remove",
        onDismiss = { confirmRemove = false },
        onConfirm = downloads::removeAll,
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
        label = "Playlist",
        songs = p.songs,
        cover = { Artwork(art, it, RoundedCornerShape(14.dp)) },
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

/** Genre / "show all" page, filled from a search. */
@Composable
fun CategoryScreen(title: String, query: String, onBack: () -> Unit) {
    val vm: SearchViewModel = viewModel(key = "category:$query") { SearchViewModel(query) }
    val state by vm.state.collectAsState()
    val actions = LocalSongActions.current
    Box(Modifier.fillMaxSize()) {
        SongResults(state, title, onLoadMore = vm::loadMore, onRetry = vm::retry, header = {
            CollectionHeader(
                title = title,
                label = "Genre",
                subtitle = if (state.songs.isEmpty()) "" else "${state.songs.size} songs",
                cover = null,
                canPlay = state.songs.isNotEmpty(),
                onPlay = { actions.play(state.songs, 0, title) },
                onShuffle = { actions.play(state.songs.shuffled(), 0, title) },
                downloadSongs = state.songs,
            )
        })
        BackButton(onBack)
    }
}

@Composable
private fun SongCollection(
    title: String,
    label: String,
    songs: List<Song>,
    cover: @Composable (Modifier) -> Unit,
    emptyText: String,
    onBack: () -> Unit,
    playlistId: String? = null,
    showDownloadButton: Boolean = true,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
    extra: (LazyListScope.() -> Unit)? = null,
) {
    val actions = LocalSongActions.current
    val totalSec = songs.sumOf { it.durationSec }
    val subtitle = buildString {
        append("${songs.size} songs")
        if (totalSec > 0) append(" · ${totalSec.formatDuration()}")
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                CollectionHeader(
                    title, subtitle, cover,
                    label = label,
                    canPlay = songs.isNotEmpty(),
                    onPlay = { actions.play(songs, 0, title) },
                    onShuffle = { actions.play(songs.shuffled(), 0, title) },
                    menu = menu,
                    downloadSongs = if (showDownloadButton) songs else null,
                )
            }
            extra?.invoke(this)
            if (songs.isEmpty()) {
                item { Text(emptyText, color = WavemothColors.TextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
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
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCover(icon, Modifier.size(46.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = WavemothColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
        }
    }
}

/**
 * Top of a song list: cover, a small red label, the title, then Play / Shuffle and the
 * download and ⋮ buttons.
 */
@Composable
internal fun CollectionHeader(
    title: String,
    subtitle: String,
    cover: (@Composable (Modifier) -> Unit)?,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    label: String? = null,
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
    downloadSongs: List<Song>? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 60.dp, start = 20.dp, end = 12.dp),
    ) {
        if (cover != null) {
            Box(Modifier.fillMaxWidth().padding(end = 8.dp), contentAlignment = Alignment.Center) {
                cover(Modifier.size(200.dp))
            }
            Spacer(Modifier.height(28.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }
        if (label != null) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = WavemothColors.Red)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            title,
            style = if (cover == null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
            color = WavemothColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Play", Icons.Filled.PlayArrow, onPlay, filled = true, enabled = canPlay)
            Spacer(Modifier.width(10.dp))
            PillButton("Shuffle", Icons.Filled.Shuffle, onShuffle, enabled = canPlay)
            Spacer(Modifier.weight(1f))
            actions()
            if (downloadSongs != null) DownloadAllButton(downloadSongs)
            if (menu != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = WavemothColors.TextSecondary) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = WavemothColors.Elevated) { menu { open = false } }
                }
            }
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.statusBarsPadding().padding(8.dp).size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f)),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = WavemothColors.TextPrimary)
    }
}
