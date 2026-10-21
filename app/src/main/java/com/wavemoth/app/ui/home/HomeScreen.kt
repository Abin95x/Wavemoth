package com.wavemoth.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Playlist
import com.wavemoth.app.data.Song
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.LikedSongsCover
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.PlaylistCover
import com.wavemoth.app.ui.components.SectionTitle
import com.wavemoth.app.ui.components.SongCard
import com.wavemoth.app.ui.components.pressClick
import com.wavemoth.app.ui.theme.WavemothColors
import java.util.Calendar

/** Home shows only your own music: Liked Songs, your playlists and what you played recently. */
@Composable
fun HomeScreen(
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val actions = LocalSongActions.current
    val library = WavemothApp.instance.library
    val recent by library.recent.collectAsState()
    val playlists by library.playlists.collectAsState()
    val headerColor = Color(0xFF2B3A5C)

    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(0f to headerColor, 0.3f to WavemothColors.Background)),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            }
        }

        // Quick-access grid: Liked Songs, then your playlists, then recent songs (up to 6 tiles).
        item {
            val cells = buildList<@Composable (Modifier) -> Unit> {
                add { m -> QuickTile("Liked Songs", m, onOpenLiked) { LikedSongsCover(Modifier.size(56.dp), RoundedCornerShape(0.dp)) } }
                playlists.take(3).forEach { p ->
                    add { m ->
                        QuickTile(p.name, m, { onOpenPlaylist(p.id) }) {
                            PlaylistCover(p.songs.firstOrNull()?.thumbnail, Modifier.size(56.dp), RoundedCornerShape(0.dp))
                        }
                    }
                }
                val recentTiles = recent.take(6 - size)
                recentTiles.forEachIndexed { i, song ->
                    add { m ->
                        QuickTile(song.title, m, { actions.play(recentTiles, i, "Recently played") }) {
                            Artwork(song.thumbnail, Modifier.size(56.dp), RoundedCornerShape(0.dp))
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { it(Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item { SectionTitle("Your playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { p -> PlaylistCard(p) { onOpenPlaylist(p.id) } }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            item { SongShelf(recent, "Recently played") }
        }

        if (recent.isEmpty() && playlists.isEmpty()) {
            item { EmptyHome(onOpenSearch) }
        }
    }
}

@Composable
private fun SongShelf(songs: List<Song>, source: String) {
    val actions = LocalSongActions.current
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            SongCard(song, onClick = { actions.play(songs, i, source) })
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).pressClick(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlaylistCover(playlist.songs.firstOrNull()?.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
    }
}

@Composable
private fun QuickTile(title: String, modifier: Modifier, onClick: () -> Unit, cover: @Composable () -> Unit) {
    Row(
        modifier.height(56.dp).pressClick(onClick = onClick).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyHome(onOpenSearch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your music will show up here", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Search for songs, like them or add them to playlists.",
            color = WavemothColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) { Text("Search songs", style = MaterialTheme.typography.titleSmall) }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
