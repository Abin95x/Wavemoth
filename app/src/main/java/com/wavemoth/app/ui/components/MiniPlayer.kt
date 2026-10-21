package com.wavemoth.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.playback.PlayerUiState
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val background by animateColorAsState(rememberArtworkColor(song.thumbnail, WavemothColors.Card), tween(500), label = "miniBg")

    // Swipe left for the next song, right for the previous one. The song row follows the finger.
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var backward by remember { mutableStateOf(false) }  // last swipe went to the previous song
    LaunchedEffect(backward) { if (backward) { delay(1_000); backward = false } }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)

    Column(
        modifier
            .padding(horizontal = 8.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next song") { next(); true },
                    CustomAccessibilityAction("Previous song") { backward = true; previous(); true },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragX <= -threshold -> next()
                            dragX >= threshold -> { backward = true; previous() }
                        }
                        scope.launch { animate(dragX, 0f, animationSpec = spring()) { v, _ -> dragX = v } }
                    },
                    onDragCancel = { scope.launch { animate(dragX, 0f, animationSpec = spring()) { v, _ -> dragX = v } } },
                ) { change, amount ->
                    change.consume()
                    dragX += amount
                }
            }
            .pressClick(scaleDown = 0.98f, onClick = onOpen)
            .clip(RoundedCornerShape(6.dp))
            .drawBehind { drawRect(background) },  // colour changes redraw only, no relayout
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // New song slides in from the right (from the left after swiping back).
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    val dir = if (backward) -1 else 1
                    (slideInHorizontally(tween(Motion.MEDIUM)) { dir * it / 3 } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                        (slideOutHorizontally(tween(Motion.MEDIUM)) { -dir * it / 3 } + fadeOut(tween(Motion.FAST)))
                },
                contentKey = { it.id },
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = dragX
                        alpha = 1f - (abs(dragX) / (threshold * 3)).coerceAtMost(0.6f)
                    },
                label = "miniSong",
            ) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(s.thumbnail, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        MarqueeText(s.title, MaterialTheme.typography.titleSmall, Color.White)
                        MarqueeText(s.artist, MaterialTheme.typography.bodySmall, Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            val likeScale by rememberLikeScale(liked)
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                    tint = if (liked) WavemothColors.Green else Color.White,
                    modifier = Modifier.graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
            IconButton(onClick = onTogglePlay) {
                if (state.isBuffering && !state.isPlaying) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    PlayPauseIcon(state.isPlaying, Color.White, Modifier.size(30.dp))
                }
            }
        }
        SmoothProgressLine(progress)
        Spacer(Modifier.height(1.dp))
    }
}

/** Thin progress line. Moves every frame but only redraws itself. */
@Composable
private fun SmoothProgressLine(progress: PlaybackProgress) {
    val position = rememberSmoothPosition(progress)
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .drawBehind {
                val fraction = if (progress.durationMs > 0) (position.value.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
                drawRect(Color.White, Offset.Zero, Size(size.width * fraction, size.height))
            },
    )
}
