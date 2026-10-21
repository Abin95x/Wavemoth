package com.wavemoth.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.wavemoth.app.playback.PlaybackProgress

/** Shared motion settings so everything moves with the same calm, unbouncy feel. */
object Motion {
    /** Fast start, long gentle settle. Used for anything that moves across the screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val pressSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val softSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    const val FAST = 160
    const val MEDIUM = 280
    const val SLOW = 420
}

/** Click that dims and shrinks the element slightly while pressed. */
fun Modifier.pressClick(scaleDown: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val t by animateFloatAsState(if (pressed) 1f else 0f, Motion.pressSpring, label = "press")
    this
        .graphicsLayer {
            val s = 1f - (1f - scaleDown) * t
            scaleX = s; scaleY = s
            alpha = 1f - 0.15f * t
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Shrink-on-press for components that already handle their own click (buttons). */
fun Modifier.pressScale(interaction: MutableInteractionSource, scaleDown: Float = 0.92f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleDown else 1f, Motion.pressSpring, label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Playback position that advances every frame while playing. Read it from draw or layout code
 * (drawBehind / graphicsLayer) so only that element updates. A new sample (a seek, say) shows up
 * in the very same frame, so nothing flicks back to the old position.
 */
@Composable
fun rememberSmoothPosition(progress: PlaybackProgress): State<Long> {
    val latest = rememberUpdatedState(progress)
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(progress) {
        now.longValue = SystemClock.elapsedRealtime()
        if (!progress.playing) return@LaunchedEffect
        while (true) withFrameMillis { now.longValue = SystemClock.elapsedRealtime() }
    }
    return remember { derivedStateOf { latest.value.positionAt(now.longValue) } }
}

/** Play/pause icon that swaps with a quick scale and fade. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(tween(Motion.MEDIUM, easing = Motion.Emphasized), initialScale = 0.7f) + fadeIn(tween(Motion.FAST))) togetherWith
                (scaleOut(tween(Motion.FAST), targetScale = 0.7f) + fadeOut(tween(Motion.FAST)))
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

/** Heart that swells a little when it becomes liked. */
@Composable
fun rememberLikeScale(liked: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    val previous = remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        val becameLiked = liked && !previous.value
        previous.value = liked
        if (becameLiked) {
            scale.animateTo(1.22f, tween(140, easing = Motion.Emphasized))
            scale.animateTo(1f, Motion.softSpring)
        }
    }
    return scale.asState()
}
