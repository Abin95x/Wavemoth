package com.wavemoth.app.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Black and red. One accent colour, used sparingly. */
object WavemothColors {
    /** Accent for text, icons and thin lines on black (5.8:1 contrast). */
    val Red = Color(0xFFFF3344)
    /** Accent for filled shapes that carry white text or icons. */
    val RedDeep = Color(0xFFC4121F)
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0B0B0B)
    val Elevated = Color(0xFF151515)
    val Card = Color(0xFF1B1B1B)
    val Hairline = Color(0xFF242424)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF9A9A9A)
    val TextTertiary = Color(0xFF5E5E5E)
    val Divider = Hairline
}

private val scheme = darkColorScheme(
    primary = WavemothColors.Red,
    onPrimary = Color.White,
    primaryContainer = WavemothColors.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = WavemothColors.Red,
    onSecondary = Color.White,
    tertiary = WavemothColors.Red,
    background = WavemothColors.Background,
    onBackground = WavemothColors.TextPrimary,
    surface = WavemothColors.Surface,
    onSurface = WavemothColors.TextPrimary,
    surfaceVariant = WavemothColors.Elevated,
    onSurfaceVariant = WavemothColors.TextSecondary,
    surfaceContainerLowest = WavemothColors.Background,
    surfaceContainerLow = WavemothColors.Surface,
    surfaceContainer = WavemothColors.Elevated,
    surfaceContainerHigh = WavemothColors.Elevated,
    surfaceContainerHighest = WavemothColors.Card,
    outline = WavemothColors.Hairline,
    outlineVariant = WavemothColors.Hairline,
    error = WavemothColors.Red,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
)

private val selection = TextSelectionColors(handleColor = WavemothColors.Red, backgroundColor = WavemothColors.Red.copy(alpha = 0.35f))

@Composable
fun WavemothTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Sheets and dialogs are drawn outside any Surface, so set light text for the whole app here.
        CompositionLocalProvider(
            LocalContentColor provides WavemothColors.TextPrimary,
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
