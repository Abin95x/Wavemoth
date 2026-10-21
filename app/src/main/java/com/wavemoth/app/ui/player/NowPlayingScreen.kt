package com.wavemoth.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.wavemoth.app.download.DownloadStatus
import com.wavemoth.app.playback.PlaybackProgress
import com.wavemoth.app.playback.PlayerControls
import com.wavemoth.app.playback.PlayerUiState
import com.wavemoth.app.ui.components.Artwork
import com.wavemoth.app.ui.components.MarqueeText
import com.wavemoth.app.ui.components.Motion
import com.wavemoth.app.ui.components.PlayPauseIcon
import com.wavemoth.app.ui.components.SeekBar
import com.wavemoth.app.ui.components.SheetOverlay
import com.wavemoth.app.ui.components.SongRow
import com.wavemoth.app.ui.components.TrackSwiper
import com.wavemoth.app.ui.components.pressScale
import com.wavemoth.app.ui.components.rememberDownloadStatus
import com.wavemoth.app.ui.components.rememberLikeScale
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    progress: PlaybackProgress,
    player: PlayerControls,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
) {
    val song = state.current ?: return
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Swipe down: the screen follows the finger, then either closes or springs back.
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // A soft red glow behind the cover that breathes slowly while music plays.
    val breathe = rememberInfiniteTransition(label = "glow")
    val pulse by breathe.animateFloat(0f, 1f, InfiniteRepeatableSpec(tween(3_200, easing = Motion.Emphasized), RepeatMode.Reverse), label = "pulse")
    val glow by animateFloatAsState(if (state.isPlaying) 1f else 0.35f, tween(Motion.SLOW * 2), label = "glowLevel")

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragY.value
                    alpha = 1f - (dragY.value / (size.height * 1.5f)).coerceIn(0f, 0.5f)
                }
                .background(WavemothColors.Background)
                .drawBehind {
                    val strength = glow * (0.16f + 0.08f * pulse)
                    drawRect(
                        Brush.radialGradient(
                            listOf(WavemothColors.RedDeep.copy(alpha = strength), Color.Transparent),
                            center = Offset(size.width / 2, size.height * 0.33f),
                            radius = size.width * 0.95f,
                        ),
                    )
                }
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
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close player", tint = WavemothColors.TextPrimary, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = WavemothColors.Red)
                    Text(
                        state.source.ifBlank { "Wavemoth" },
                        style = MaterialTheme.typography.titleSmall,
                        color = WavemothColors.TextSecondary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, "More options", tint = WavemothColors.TextPrimary) }
            }

            Spacer(Modifier.weight(1f))
            // Swipe the cover for the next or previous song. It eases back a little while paused.
            val artScale by animateFloatAsState(if (state.isPlaying || state.isBuffering) 1f else 0.9f, Motion.softSpring, label = "artScale")
            TrackSwiper(
                current = song,
                previous = state.previousSong,
                next = state.nextSong,
                onNext = player::next,
                onPrevious = player::skipToPrevious,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            ) { s ->
                Artwork(
                    s.thumbnail,
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = artScale; scaleY = artScale },
                    RoundedCornerShape(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = song,
                    contentKey = { it.id },
                    transitionSpec = {
                        (fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it / 3 }) togetherWith
                            fadeOut(tween(Motion.FAST))
                    },
                    modifier = Modifier.weight(1f),
                    label = "titles",
                ) { s ->
                    Column {
                        MarqueeText(s.title, MaterialTheme.typography.titleLarge, WavemothColors.TextPrimary)
                        MarqueeText(s.artist, MaterialTheme.typography.bodyLarge, WavemothColors.TextSecondary)
                    }
                }
                val likeScale by rememberLikeScale(liked)
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                        tint = if (liked) WavemothColors.Red else WavemothColors.TextPrimary,
                        modifier = Modifier.size(26.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                    )
                }
            }

            SeekBar(progress, player::seekTo, Modifier.padding(top = 12.dp))

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = WavemothColors.RedDeep, contentColor = Color.White),
                ) {
                    if (state.isBuffering && !state.isPlaying) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp)
                    } else {
                        PlayPauseIcon(state.isPlaying, Color.White, Modifier.size(34.dp))
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

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Filled.Lyrics, "Lyrics", tint = WavemothColors.TextSecondary)
                }
                DownloadIcon(rememberDownloadStatus(song.id), onDownload)
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = WavemothColors.TextSecondary)
                }
            }
        }

        // Lyrics slide up over the player.
        AnimatedVisibility(
            visible = showLyrics,
            enter = slideInVertically(tween(Motion.SLOW, easing = Motion.Emphasized)) { it } + fadeIn(tween(Motion.MEDIUM)),
            exit = slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it } + fadeOut(tween(Motion.MEDIUM)),
        ) {
            BackHandler { showLyrics = false }
            LyricsPanel(song, progress, onSeek = player::seekTo, onClose = { showLyrics = false })
        }
    }

    SheetOverlay(visible = showQueue, onDismiss = { showQueue = false }) {
        Text("QUEUE", style = MaterialTheme.typography.labelSmall, color = WavemothColors.TextSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        LazyColumn {
            itemsIndexed(state.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                SongRow(s, onClick = { player.skipTo(i) }, playing = i == state.queueIndex, index = i, modifier = Modifier.animateItem())
            }
        }
    }
}

/** Download state of the current song: tap to download, red tick when saved. */
@Composable
private fun DownloadIcon(status: DownloadStatus?, onDownload: () -> Unit) {
    when (status) {
        DownloadStatus.Done -> IconButton(onClick = {}) { Icon(Icons.Filled.DownloadDone, "Downloaded", tint = WavemothColors.Red) }
        DownloadStatus.Queued, DownloadStatus.Downloading -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), color = WavemothColors.Red, trackColor = WavemothColors.Hairline, strokeWidth = 2.dp)
        }
        else -> IconButton(onClick = onDownload) { Icon(Icons.Outlined.FileDownload, "Download", tint = WavemothColors.TextSecondary) }
    }
}

@Composable
private fun PressIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, interactionSource = press, modifier = Modifier.size(56.dp).pressScale(press, 0.85f)) {
        Icon(icon, label, tint = WavemothColors.TextPrimary, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) WavemothColors.Red else WavemothColors.TextSecondary, tween(Motion.MEDIUM), label = "toggleTint")
    val dot by animateFloatAsState(if (active) 1f else 0f, Motion.softSpring, label = "toggleDot")
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(3.dp))
            // Small dot under the icon while the mode is on.
            Box(Modifier.size(4.dp).graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }.background(WavemothColors.Red, CircleShape))
        }
    }
}
