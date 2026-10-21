package com.wavemoth.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wavemoth.app.ui.theme.WavemothColors

/**
 * Sheets and dialogs are drawn inside the app's own window, on top of everything, instead of in
 * separate pop-up windows. They animate in and out smoothly and behave the same in tests.
 */
class OverlayHost {
    private class Entry(val content: State<@Composable () -> Unit>)
    private val entries = mutableStateListOf<Entry>()

    @Composable
    internal fun Register(content: @Composable () -> Unit) {
        val current = rememberUpdatedState(content)
        DisposableEffect(this) {
            val entry = Entry(current)
            entries.add(entry)
            onDispose { entries.remove(entry) }
        }
    }

    /** Draws all open sheets and dialogs. Place last, so it sits above everything else. */
    @Composable
    fun Render() {
        entries.forEach { it.content.value() }
    }
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> { error("OverlayHost not provided") }

/**
 * Keeps an overlay in place while it is visible or still animating out. The dimmed background is
 * the parent, so taps on the sheet/dialog reach it first and only taps outside close it.
 */
@Composable
private fun AnimatedOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable (MutableTransitionState<Boolean>) -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    LocalOverlayHost.current.Register {
        val scrim = remember { Animatable(0f) }
        LaunchedEffect(visible) { scrim.animateTo(if (visible) 0.6f else 0f, tween(200)) }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = scrim.value)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            content(state)
        }
        BackHandler(enabled = visible, onBack = onDismiss)
    }
}

/** Bottom sheet that slides up over the app. */
@Composable
fun SheetOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedOverlay(visible, onDismiss) { state ->
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                state,
                enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { it },
                exit = slideOutVertically(tween(220)) { it },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(WavemothColors.Elevated)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}  // don't close when tapping the sheet
                        .navigationBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    // Drag handle look.
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                            .size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                    content()
                }
            }
        }
    }
}

/** Centered dialog card that fades and scales in. */
@Composable
fun DialogOverlay(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AnimatedOverlay(visible, onDismiss) { state ->
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                state,
                enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WavemothColors.Elevated)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(20.dp),
                    content = content,
                )
            }
        }
    }
}
