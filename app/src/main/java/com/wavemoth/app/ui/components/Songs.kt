@file:OptIn(ExperimentalFoundationApi::class)

package com.wavemoth.app.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavemoth.app.data.Song
import com.wavemoth.app.download.DownloadStatus
import com.wavemoth.app.ui.theme.WavemothColors

/** Things any song in any list can do; provided once at the top of the app. */
class SongActions(
    /** Play [songs] starting at index; the string is the "Playing from" label. */
    val play: (songs: List<Song>, index: Int, source: String) -> Unit,
    val showOptions: (song: Song, playlistId: String?) -> Unit,
    val currentSongId: () -> String?,
    val isPlaying: () -> Boolean = { false },
)

val LocalSongActions = staticCompositionLocalOf<SongActions> { error("SongActions not provided") }

/** Download status of every song, as a State so rows can watch only their own entry. */
val LocalDownloadStatus = staticCompositionLocalOf<State<Map<String, DownloadStatus>>> { mutableStateOf(emptyMap()) }

/** This song's download status; recomposes only when it changes. */
@Composable
fun rememberDownloadStatus(id: String): DownloadStatus? {
    val all = LocalDownloadStatus.current
    val status by remember(id, all) { derivedStateOf { all.value[id] } }
    return status
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    index: Int? = null,
    onMore: (() -> Unit)? = null,
) {
    val active by animateFloatAsState(if (playing) 1f else 0f, tween(Motion.MEDIUM), label = "rowActive")
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawBehind {
                // Thin red mark on the left edge of the playing song.
                if (active > 0f) {
                    val h = size.height * 0.5f * active
                    drawRoundRect(WavemothColors.Red, Offset(0f, (size.height - h) / 2), Size(3.dp.toPx(), h), CornerRadius(2.dp.toPx()))
                }
            }
            .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelMedium,
                color = if (playing) WavemothColors.Red else WavemothColors.TextTertiary,
                modifier = Modifier.width(30.dp),
            )
        }
        Artwork(song.thumbnail, Modifier.size(46.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    EqualizerBars(LocalSongActions.current.isPlaying(), Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) WavemothColors.Red else WavemothColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadBadge(song.id)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = WavemothColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Filled.MoreVert, "More options", tint = WavemothColors.TextTertiary)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Small mark before the artist name: red tick when saved, grey arrow while downloading. */
@Composable
fun DownloadBadge(id: String) {
    when (rememberDownloadStatus(id)) {
        DownloadStatus.Done -> Icon(Icons.Filled.DownloadDone, "Downloaded", tint = WavemothColors.Red, modifier = Modifier.padding(end = 4.dp).size(13.dp))
        DownloadStatus.Queued, DownloadStatus.Downloading ->
            Icon(Icons.Filled.ArrowDownward, "Downloading", tint = WavemothColors.TextSecondary, modifier = Modifier.padding(end = 4.dp).size(13.dp))
        else -> Unit
    }
}

/** Three bars that bounce while music plays and rest when paused. Only redraws itself. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = WavemothColors.Red) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = listOf(520, 380, 640).map { ms ->
        transition.animateFloat(0.25f, 1f, InfiniteRepeatableSpec(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "bar")
    }
    val level by animateFloatAsState(if (playing) 1f else 0f, tween(Motion.MEDIUM), label = "eqLevel")
    Box(
        modifier.drawBehind {
            val gap = size.width / 7
            val w = gap * 1.6f
            bars.forEachIndexed { i, bar ->
                val h = size.height * (0.3f + (bar.value - 0.3f) * level).coerceIn(0.2f, 1f)
                drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        },
    )
}

/** Square cover with title and artist underneath, for horizontal shelves. */
@Composable
fun SongCard(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(136.dp).pressClick(onClick = onClick), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Artwork(song.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f).padding(bottom = 6.dp), RoundedCornerShape(10.dp))
        Text(song.title, style = MaterialTheme.typography.titleSmall, color = WavemothColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = WavemothColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Single line of text that scrolls sideways when it is too long. */
@Composable
fun MarqueeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, modifier = modifier.basicMarquee(initialDelayMillis = 2_000))
}

/** Small, spaced-out uppercase label that heads a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 28.dp, bottom = 12.dp).height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 12.dp).drawBehind { drawRoundRect(WavemothColors.Red, cornerRadius = CornerRadius(2.dp.toPx())) })
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = WavemothColors.TextSecondary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
