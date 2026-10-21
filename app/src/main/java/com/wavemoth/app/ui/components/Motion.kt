package com.wavemoth.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.wavemoth.app.playback.PlaybackProgress

/** Shared motion settings so everything moves with the same feel. */
object Motion {
    val pressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val softSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    const val FAST = 180
    const val MEDIUM = 300
}

/** Click that also shrinks the element slightly while pressed, then springs back. */
fun Modifier.pressClick(scaleDown: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.pressSpring, label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Shrink-on-press for components that already handle their own click (buttons). */
fun Modifier.pressScale(interaction: MutableInteractionSource, scaleDown: Float = 0.9f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.pressSpring, label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}


/**
 * Playback position that advances every frame while playing. Read it from draw or layout code
 * (e.g. drawBehind / graphicsLayer) so only that element updates.
 */
@Composable
fun rememberSmoothPosition(progress: PlaybackProgress): State<Long> {
    val position = remember { mutableLongStateOf(progress.positionAt(SystemClock.elapsedRealtime())) }
    LaunchedEffect(progress) {
        position.longValue = progress.positionAt(SystemClock.elapsedRealtime())
        if (!progress.playing) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            position.longValue = progress.positionAt(SystemClock.elapsedRealtime())
        }
    }
    return position
}

/** Play/pause icon that swaps with a quick scale and fade. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(tween(Motion.FAST), initialScale = 0.6f) + fadeIn(tween(Motion.FAST))) togetherWith
                (scaleOut(tween(Motion.FAST), targetScale = 0.6f) + fadeOut(tween(Motion.FAST)))
        },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = modifier,
        )
    }
}

/** Heart that pops when it becomes liked. */
@Composable
fun rememberLikeScale(liked: Boolean): State<Float> {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val previous = remember { androidx.compose.runtime.mutableStateOf(liked) }
    LaunchedEffect(liked) {
        val becameLiked = liked && !previous.value
        previous.value = liked
        if (becameLiked) {
            scale.animateTo(1.3f, tween(110, easing = LinearEasing))
            scale.animateTo(1f, Motion.pressSpring)
        }
    }
    return scale.asState()
}
