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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wavemoth.app.WavemothApp
import com.wavemoth.app.data.Song
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.ui.components.PillButton
import com.wavemoth.app.ui.components.pressClick
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.flow.StateFlow

/** Home: shortcuts to your music and a visualizer for what's playing. */
@Composable
fun HomeScreen(
    current: Song?,
    isPlaying: Boolean,
    progress: StateFlow<PlaybackProgress>,
    onOpenPlayer: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = WavemothApp.instance
    val liked by app.library.liked.collectAsState()
    val downloaded by app.downloads.songs.collectAsState()

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

        item { NowPlaying(current, isPlaying, progress, onOpenPlayer, onOpenSearch) }
    }
}

/** The visualizer with the playing song under it; tap to open the player. */
@Composable
private fun NowPlaying(
    current: Song?,
    isPlaying: Boolean,
    progress: StateFlow<PlaybackProgress>,
    onOpenPlayer: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val open = if (current != null) Modifier.pressClick(onClick = onOpenPlayer) else Modifier
        Visualizer(
            playing = isPlaying,
            progress = { progress.value },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(1f)
                .then(open)
                .semantics { contentDescription = if (current != null) "Music visualizer. Open player" else "Music visualizer" },
        )
        if (current != null) {
            Text(
                current.title,
                style = MaterialTheme.typography.titleMedium,
                color = WavemothColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                current.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = WavemothColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            Text("Nothing playing", style = MaterialTheme.typography.titleMedium, color = WavemothColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            PillButton("Search songs", Icons.Filled.Search, onOpenSearch, filled = true)
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
