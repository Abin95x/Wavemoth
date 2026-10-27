@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.wavemoth.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Playlist
import com.wavemoth.app.data.Song
import com.wavemoth.app.data.TopArtist
import com.wavemoth.app.data.TopArtists
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.LocalSongActions
import com.wavemoth.app.ui.components.PillButton
import com.wavemoth.app.ui.components.PlaylistCover
import com.wavemoth.app.ui.components.SectionLabel
import com.wavemoth.app.ui.components.SongCard
import com.wavemoth.app.ui.components.pressClick
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many recently played songs the Home shelf shows; the full history is in Library. */
private const val HOME_RECENT_LIMIT = 15

/** Home shows only your own music: shortcuts, your top artists, what you played recently and your playlists. */
@Composable
fun HomeScreen(
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArtist: (name: String, image: String) -> Unit,
) {
    val app = WavemothApp.instance
    val recent by app.library.recent.collectAsState()
    val playlists by app.library.playlists.collectAsState()
    val liked by app.library.liked.collectAsState()
    val downloaded by app.downloads.songs.collectAsState()
    val artists = remember(liked, recent) { TopArtists.from(liked, recent) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append("wavemoth")
                        withStyle(SpanStyle(color = WavemothColors.Red)) { append(".") }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = WavemothColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = WavemothColors.TextSecondary) }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Filled.Favorite, "Liked Songs", "${liked.size} songs", Modifier.weight(1f), onOpenLiked)
                Shortcut(Icons.Filled.Download, "Downloads", "${downloaded.size} songs", Modifier.weight(1f), onOpenDownloads)
            }
        }

        if (artists.isNotEmpty()) {
            item { SectionLabel("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artists, key = { it.name.lowercase() }) { a -> ArtistCard(a, onOpenArtist) }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionLabel("Recently played") }
            item { SongShelf(recent.take(HOME_RECENT_LIMIT), "Recently played") }
        }

        if (playlists.isNotEmpty()) {
            item { SectionLabel("Playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { p -> PlaylistCard(p) { onOpenPlaylist(p.id) } }
                }
            }
        }

        if (recent.isEmpty() && playlists.isEmpty()) {
            item { EmptyHome(onOpenSearch) }
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .pressClick(onClick = onClick)
            .clip(shape)
            .background(WavemothColors.Surface)
            .border(1.dp, WavemothColors.Hairline, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = WavemothColors.Red, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, maxLines = 1)
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

/** Round artist photo (looked up on YouTube Music, song cover until then) with the name below. */
@Composable
private fun ArtistCard(artist: TopArtist, onOpen: (name: String, image: String) -> Unit) {
    val photo by produceState<String?>(null, artist.name) { value = withContext(Dispatchers.IO) { TopArtists.photo(artist.name) } }
    val image = photo ?: artist.cover
    Column(
        Modifier.width(96.dp).pressClick(onClick = { onOpen(artist.name, image.orEmpty()) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(image, Modifier.size(96.dp), CircleShape)
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = WavemothColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.width(136.dp).pressClick(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PlaylistCover(playlist.songs.firstOrNull()?.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).padding(bottom = 6.dp), RoundedCornerShape(10.dp))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1)
        Text("${playlist.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary)
    }
}

@Composable
private fun EmptyHome(onOpenSearch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nothing here yet", style = MaterialTheme.typography.headlineMedium, color = WavemothColors.TextPrimary)
        Text("Find a song, like it or add it to a playlist and it shows up here.", color = WavemothColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        PillButton("Search songs", Icons.Filled.Search, onOpenSearch, filled = true)
    }
}
