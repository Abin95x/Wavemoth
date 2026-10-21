package com.wavemoth.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.playback.PlayerUiState
import com.wavemoth.app.ui.theme.WavemothColors

private val MiniShape = RoundedCornerShape(14.dp)

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    progress: PlaybackProgress,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val position = rememberSmoothPosition(progress)

    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next song") { next(); true },
                    CustomAccessibilityAction("Previous song") { previous(); true },
                )
            }
            .pressClick(scaleDown = 0.985f, onClick = onOpen)
            .clip(MiniShape)
            .background(WavemothColors.Elevated)
            .border(1.dp, WavemothColors.Hairline, MiniShape)
            // Progress as a red line along the bottom edge. Redraws every frame, never recomposes.
            .drawBehind {
                val d = progress.durationMs
                val f = if (d > 0) (position.value.toFloat() / d).coerceIn(0f, 1f) else 0f
                val h = 2.dp.toPx()
                drawLine(WavemothColors.Red, Offset(0f, size.height - h / 2), Offset(size.width * f, size.height - h / 2), h)
            },
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Swipe left for the next song, right for the previous one.
            TrackSwiper(
                current = song,
                previous = state.previousSong,
                next = state.nextSong,
                onNext = onNext,
                onPrevious = onPrevious,
                modifier = Modifier.weight(1f),
            ) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(s.thumbnail, Modifier.size(42.dp), RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        MarqueeText(s.title, MaterialTheme.typography.titleSmall, WavemothColors.TextPrimary)
                        MarqueeText(s.artist, MaterialTheme.typography.bodySmall, WavemothColors.TextSecondary)
                    }
                }
            }
            val likeScale by rememberLikeScale(liked)
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                    tint = if (liked) WavemothColors.Red else WavemothColors.TextSecondary,
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
            IconButton(onClick = onTogglePlay) {
                if (state.isBuffering && !state.isPlaying) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = WavemothColors.Red, strokeWidth = 2.dp)
                } else {
                    PlayPauseIcon(state.isPlaying, WavemothColors.TextPrimary, Modifier.size(28.dp))
                }
            }
        }
    }
}
