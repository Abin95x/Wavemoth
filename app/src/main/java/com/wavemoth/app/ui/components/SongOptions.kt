package com.wavemoth.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.foundation.shape.RoundedCornerShape
import com.wavemoth.app.download.DownloadStatus
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.Playlist
import com.wavemoth.app.data.Song
import com.wavemoth.app.ui.theme.WavemothColors

/** Actions for one song. Shown while [song] is not null. */
@Composable
fun SongOptionsSheet(
    song: Song?,
    liked: Boolean,
    inPlaylist: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToArtist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    // Keep showing the last song while the sheet slides away.
    var shown by remember { mutableStateOf(song) }
    if (song != null) shown = song
    val s = shown ?: return

    SheetOverlay(visible = song != null, onDismiss = onDismiss) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(s.thumbnail, Modifier.size(52.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, maxLines = 1)
            }
        }
        HorizontalDivider(color = WavemothColors.Hairline, modifier = Modifier.padding(vertical = 8.dp))
        fun act(block: () -> Unit) = { block(); onDismiss() }
        OptionRow(
            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (liked) "Remove from Liked Songs" else "Add to Liked Songs",
            act(onToggleLike),
            tint = if (liked) WavemothColors.Red else WavemothColors.TextSecondary,
        )
        OptionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", act(onAddToPlaylist))
        if (inPlaylist) OptionRow(Icons.Filled.RemoveCircleOutline, "Remove from this playlist", act(onRemoveFromPlaylist))
        OptionRow(Icons.Filled.SkipNext, "Play next", act(onPlayNext))
        OptionRow(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", act(onAddToQueue))
        when (rememberDownloadStatus(s.id)) {
            DownloadStatus.Done -> OptionRow(Icons.Filled.DownloadDone, "Remove download", act(onRemoveDownload), tint = WavemothColors.Red)
            DownloadStatus.Queued, DownloadStatus.Downloading -> OptionRow(Icons.Filled.Close, "Cancel download", act(onRemoveDownload))
            else -> OptionRow(Icons.Filled.Download, "Download", act(onDownload))
        }
        OptionRow(Icons.Filled.Person, "Go to artist", act(onGoToArtist))
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = WavemothColors.TextSecondary) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = WavemothColors.TextPrimary)
    }
}

@Composable
fun AddToPlaylistDialog(
    visible: Boolean,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
) {
    DialogOverlay(visible, onDismiss) {
        Text("Add to playlist", style = MaterialTheme.typography.titleLarge, color = WavemothColors.TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconCover(Icons.Filled.Add, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("New playlist", style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary)
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(playlist) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(playlist.songs.firstOrNull()?.thumbnail, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WavemothColors.TextSecondary) }
        }
    }
}

/** Asks for a name (new playlist, rename). The field starts as [initial] each time it opens. */
@Composable
fun NameDialog(
    visible: Boolean,
    title: String,
    initial: String = "",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(visible) { mutableStateOf(initial) }
    DialogOverlay(visible, onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = WavemothColors.TextPrimary, modifier = Modifier.padding(bottom = 16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text("My playlist") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WavemothColors.Red,
                unfocusedBorderColor = WavemothColors.Hairline,
                cursorColor = WavemothColors.Red,
                focusedTextColor = WavemothColors.TextPrimary,
                unfocusedTextColor = WavemothColors.TextPrimary,
                focusedPlaceholderColor = WavemothColors.TextTertiary,
                unfocusedPlaceholderColor = WavemothColors.TextTertiary,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WavemothColors.TextSecondary) }
            TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel, color = WavemothColors.Red) }
        }
    }
}

/** Yes/no question before something that can't be undone. */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogOverlay(visible, onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = WavemothColors.TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = WavemothColors.TextSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WavemothColors.TextSecondary) }
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = WavemothColors.Red) }
        }
    }
}
