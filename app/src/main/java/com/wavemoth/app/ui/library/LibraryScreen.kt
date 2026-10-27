@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wavemoth.app.ui.library

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.ImportStatus
import com.wavemoth.app.ui.components.BACKUP_TYPES
import com.wavemoth.app.ui.components.CSV_TYPES
import com.wavemoth.app.ui.components.DownloadsCover
import com.wavemoth.app.ui.components.IconCover
import com.wavemoth.app.ui.components.LikedSongsCover
import com.wavemoth.app.ui.components.NameDialog
import com.wavemoth.app.ui.components.PlaylistCover
import com.wavemoth.app.ui.components.RecentCover
import com.wavemoth.app.ui.components.SectionLabel
import com.wavemoth.app.ui.components.rememberFileOpener
import com.wavemoth.app.ui.components.rememberFileSaver
import com.wavemoth.app.ui.theme.WavemothColors

@Composable
fun LibraryScreen(
    onOpenLiked: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val app = WavemothApp.instance
    val library = app.library
    val liked by library.liked.collectAsState()
    val recent by library.recent.collectAsState()
    val playlists by library.playlists.collectAsState()
    val downloaded by app.downloads.songs.collectAsState()
    val grid by app.settings.libraryGrid.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val importer = app.importer
    val importStatus by importer.status.collectAsState()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }

    // Import a playlist CSV (title, artist, album …).
    val pickPlaylistFile = rememberFileOpener { name, text -> importer.import(name, text.orEmpty()) }
    // Whole-library backup and restore.
    val saveBackup = rememberFileSaver("application/json")
    val pickBackup = rememberFileOpener { _, text ->
        val message = runCatching { library.restoreBackup(text.orEmpty()) }
            .fold({ "Restored $it playlists and your liked songs" }, { "That file isn't a Wavemoth backup" })
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, color = WavemothColors.TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { app.settings.setLibraryGrid(!grid) }) {
                Icon(
                    if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    if (grid) "Show as list" else "Show as grid",
                    tint = WavemothColors.TextSecondary,
                )
            }
            IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Create playlist", tint = WavemothColors.TextPrimary, modifier = Modifier.size(26.dp)) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Import and export", tint = WavemothColors.TextSecondary) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = WavemothColors.Elevated) {
                    DropdownMenuItem(
                        text = { Text("Download all songs") },
                        leadingIcon = { Icon(Icons.Filled.Download, null) },
                        onClick = {
                            menuOpen = false
                            val n = app.downloads.downloadLibrary(library)
                            Toast.makeText(context, if (n == 0) "Everything is already downloaded" else "Downloading $n songs", Toast.LENGTH_SHORT).show()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import playlist file") },
                        leadingIcon = { Icon(Icons.Filled.FileOpen, null) },
                        onClick = { menuOpen = false; pickPlaylistFile(CSV_TYPES) },
                    )
                    DropdownMenuItem(
                        text = { Text("Back up library") },
                        leadingIcon = { Icon(Icons.Filled.Backup, null) },
                        onClick = { menuOpen = false; saveBackup("Wavemoth backup.json") { library.exportBackup() } },
                    )
                    DropdownMenuItem(
                        text = { Text("Restore from backup") },
                        leadingIcon = { Icon(Icons.Filled.Restore, null) },
                        onClick = { menuOpen = false; pickBackup(BACKUP_TYPES) },
                    )
                }
            }
        }

        val pinned = listOf(
            Entry("liked", "Liked Songs", "${liked.size} songs", onOpenLiked) { LikedSongsCover(it) },
            Entry("downloads", "Downloads", "${downloaded.size} songs on this phone", onOpenDownloads) { DownloadsCover(it) },
            Entry("recent", "Recently played", "${recent.size} songs", onOpenRecent) { RecentCover(it) },
            Entry("import", "Import playlist file", "Add songs from a .csv list (title, artist)", { pickPlaylistFile(CSV_TYPES) }) {
                IconCover(Icons.Filled.FileOpen, it)
            },
        )
        val playlistEntries = playlists.map { p ->
            Entry(p.id, p.name, "${p.songs.size} songs", { onOpenPlaylist(p.id) }) { PlaylistCover(p.songs.firstOrNull()?.thumbnail, it) }
        }
        // Two columns on a phone, more on wider screens.
        val columns = (LocalConfiguration.current.screenWidthDp / 180).coerceAtLeast(2)

        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            entries(pinned, grid, columns)
            importStatus?.let { status -> item(key = "import-status") { ImportStatusRow(status, onOpenPlaylist, importer::dismiss) } }
            item(key = "playlists-label") { SectionLabel("Playlists") }
            entries(playlistEntries, grid, columns)
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "Tap + to create your first playlist.",
                        color = WavemothColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    NameDialog(
        visible = creating,
        title = "Give your playlist a name",
        onDismiss = { creating = false },
        onConfirm = { name ->
            creating = false
            onOpenPlaylist(library.createPlaylist(name).id)
        },
    )
}

/** One thing in the Library (a built-in collection or a playlist), shown as a row or a grid tile. */
private class Entry(
    val key: String,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val cover: @Composable (Modifier) -> Unit,
)

/** Adds [list] as one row per entry, or in grid mode as rows of [columns] tiles. */
private fun LazyListScope.entries(list: List<Entry>, grid: Boolean, columns: Int) {
    if (!grid) {
        items(list, key = { it.key }) { LibraryRow(it, Modifier.animateItem()) }
        return
    }
    items(list.chunked(columns), key = { "row:" + it.first().key }) { row ->
        Row(
            Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            row.forEach { LibraryTile(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun LibraryRow(entry: Entry, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = entry.onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entry.cover(Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
        }
    }
}

@Composable
private fun LibraryTile(entry: Entry, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = entry.onClick)) {
        entry.cover(Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(entry.title, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Progress and result of a file import (into a new playlist or into Liked Songs). */
@Composable
internal fun ImportStatusRow(status: ImportStatus, onOpenPlaylist: (String) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        when {
            status.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.error, color = WavemothColors.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("OK", color = WavemothColors.TextPrimary) }
            }
            !status.finished -> {
                Text(
                    (if (status.intoLiked) "Adding to Liked Songs" else "Importing \"${status.name}\"") +
                        ": finding songs ${status.done} of ${status.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WavemothColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (status.total == 0) 0f else status.done.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth(),
                    color = WavemothColors.Red,
                    trackColor = WavemothColors.Hairline,
                    drawStopIndicator = {},
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                val missed = status.total - status.found
                Text(
                    (if (status.intoLiked) "Added ${status.found} of ${status.total} songs to Liked Songs" else "Imported ${status.found} of ${status.total} songs") +
                        if (missed > 0) " ($missed not found)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = WavemothColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                status.playlistId?.let { id ->
                    TextButton(onClick = { onDismiss(); onOpenPlaylist(id) }) { Text("Open", color = WavemothColors.Red) }
                }
                TextButton(onClick = onDismiss) { Text("OK", color = WavemothColors.TextPrimary) }
            }
        }
    }
}
