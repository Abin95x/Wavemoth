
package com.wavemoth.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.wavemoth.app.ui.components.SheetOverlay
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.wavemoth.app.data.formatDuration
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.playback.PlayerUiState
import com.wavemoth.app.playback.PlayerControls
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.MarqueeText
import com.wavemoth.app.ui.components.Motion
import com.wavemoth.app.ui.components.PlayPauseIcon
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.pressScale
import com.wavemoth.app.ui.components.rememberArtworkColor
import com.wavemoth.app.ui.components.rememberLikeScale
import com.wavemoth.app.ui.components.rememberSmoothPosition
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    progress: PlaybackProgress,
    player: PlayerControls,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
) {
    val song = state.current ?: return
    val tint by animateColorAsState(rememberArtworkColor(song.thumbnail, Color(0xFF404040)), tween(600), label = "npBg")
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Swipe down: the screen follows the finger, then either closes or springs back.
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragY.value
                alpha = 1f - (dragY.value / (size.height * 1.5f)).coerceIn(0f, 0.5f)
            }
            .drawBehind { drawRect(Brush.verticalGradient(listOf(tint, WavemothColors.Background))) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragY.value > size.height * 0.18f) onClose()
                            else dragY.animateTo(0f, Motion.softSpring)
                        }
                    },
                    onDragCancel = { scope.launch { dragY.animateTo(0f, Motion.softSpring) } },
                ) { change, dy ->
                    change.consume()
                    scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close player", modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text(state.source.ifBlank { "Wavemoth" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, textAlign = TextAlign.Center)
            }
            IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More options") }
        }

        Spacer(Modifier.weight(1f))
        // Cover eases down a little while paused, and new covers fade/zoom in.
        val artScale by animateFloatAsState(if (state.isPlaying || state.isBuffering) 1f else 0.88f, Motion.softSpring, label = "artScale")
        AnimatedContent(
            targetState = song.thumbnail,
            transitionSpec = { (fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = 0.94f)) togetherWith fadeOut(tween(Motion.FAST)) },
            modifier = Modifier.graphicsLayer { scaleX = artScale; scaleY = artScale },
            label = "art",
        ) { url ->
            Artwork(url, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(8.dp))
        }
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MarqueeText(song.title, MaterialTheme.typography.titleLarge, Color.White)
                MarqueeText(song.artist, MaterialTheme.typography.bodyLarge, Color.White.copy(alpha = 0.7f))
            }
            val likeScale by rememberLikeScale(liked)
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                    tint = if (liked) WavemothColors.Green else Color.White,
                    modifier = Modifier.size(28.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
        }

        SeekBar(progress, player::seekTo)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleIcon(Icons.Filled.Shuffle, "Shuffle", state.shuffle, player::toggleShuffle)
            PressIcon(Icons.Filled.SkipPrevious, "Previous", player::previous)
            val playPress = remember { MutableInteractionSource() }
            FilledIconButton(
                onClick = player::togglePlay,
                interactionSource = playPress,
                modifier = Modifier.size(68.dp).pressScale(playPress),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                if (state.isBuffering && !state.isPlaying) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                } else {
                    PlayPauseIcon(state.isPlaying, Color.Black, Modifier.size(36.dp))
                }
            }
            PressIcon(Icons.Filled.SkipNext, "Next", player::next)
            ToggleIcon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                "Repeat",
                state.repeatMode != Player.REPEAT_MODE_OFF,
                player::cycleRepeat,
            )
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { showLyrics = true }) {
                Icon(Icons.Filled.Lyrics, "Lyrics", tint = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }

    // Lyrics slide up over the player.
    AnimatedVisibility(
        visible = showLyrics,
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(tween(Motion.MEDIUM)) { it } + fadeOut(),
    ) {
        BackHandler { showLyrics = false }
        LyricsPanel(song, progress, tint, onSeek = player::seekTo, onClose = { showLyrics = false })
    }
    }

    SheetOverlay(visible = showQueue, onDismiss = { showQueue = false }) {
        Text("Queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyColumn {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                SongRow(s, onClick = { player.skipTo(i) }, playing = i == state.queueIndex, index = i, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SeekBar(progress: PlaybackProgress, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position by rememberSmoothPosition(progress)
    val duration = progress.durationMs.coerceAtLeast(1)
    val value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    Column(Modifier.padding(top = 8.dp)) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.height(28.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging != null) (value * duration).toLong() else position
            Text((shown / 1000).formatDurationOrZero(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.weight(1f))
            Text((progress.durationMs / 1000).formatDurationOrZero(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

private fun Long.formatDurationOrZero() = formatDuration().ifEmpty { "0:00" }

@Composable
private fun PressIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = press, modifier = Modifier.size(56.dp).pressScale(press, 0.82f)) {
        Icon(icon, label, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) WavemothColors.Green else Color.White.copy(alpha = 0.8f), tween(Motion.FAST), label = "toggleTint")
    val dot by animateFloatAsState(if (active) 1f else 0f, Motion.pressSpring, label = "toggleDot")
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(2.dp))
            // Small dot under the icon while the mode is on.
            Box(Modifier.size(4.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(WavemothColors.Green, CircleShape))
        }
    }
}
