package com.wavemoth.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object WavemothColors {
    val Green = Color(0xFF1ED760)
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF181818)
    val Elevated = Color(0xFF282828)
    val Card = Color(0xFF2A2A2A)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFB3B3B3)
    val Divider = Color(0xFF3E3E3E)
}

private val scheme = darkColorScheme(
    primary = WavemothColors.Green,
    onPrimary = Color.Black,
    secondary = WavemothColors.Green,
    background = WavemothColors.Background,
    onBackground = WavemothColors.TextPrimary,
    surface = WavemothColors.Surface,
    onSurface = WavemothColors.TextPrimary,
    surfaceVariant = WavemothColors.Elevated,
    onSurfaceVariant = WavemothColors.TextSecondary,
    surfaceContainer = WavemothColors.Elevated,
    surfaceContainerHigh = WavemothColors.Elevated,
    surfaceContainerLow = WavemothColors.Surface,
    outline = WavemothColors.Divider,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

@Composable
fun WavemothTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
