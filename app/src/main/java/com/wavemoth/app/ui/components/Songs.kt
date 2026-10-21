@file:OptIn(ExperimentalFoundationApi::class)

package com.wavemoth.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.Song
import com.wavemoth.app.ui.theme.WavemothColors

/** Things any song in any list can do; provided once at the top of the app. */
class SongActions(
    /** Play [songs] starting at index; the string is the "Playing from" label. */
    val play: (songs: List<Song>, index: Int, source: String) -> Unit,
    val showOptions: (song: Song, playlistId: String?) -> Unit,
    val currentSongId: () -> String?,
)

val LocalSongActions = staticCompositionLocalOf<SongActions> { error("SongActions not provided") }

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    index: Int? = null,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = WavemothColors.TextSecondary,
                modifier = Modifier.width(28.dp),
            )
        }
        Artwork(song.thumbnail, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(Icons.Filled.GraphicEq, null, tint = WavemothColors.Green, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) WavemothColors.Green else WavemothColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = WavemothColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, "More options", tint = WavemothColors.TextSecondary)
            }
        }
    }
}

/** Square cover with title and artist underneath, for horizontal shelves. */
@Composable
fun SongCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(148.dp).pressClick(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Artwork(song.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            song.title,
            style = MaterialTheme.typography.titleSmall,
            color = WavemothColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = WavemothColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** Single line of text that scrolls sideways when it is too long. */
@Composable
fun MarqueeText(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, modifier = modifier.basicMarquee())
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = WavemothColors.TextPrimary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
