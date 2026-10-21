package com.wavemoth.app.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wavemoth.app.ui.theme.WavemothColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    Box(modifier.clip(shape).background(WavemothColors.Elevated), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Filled.MusicNote, null, tint = WavemothColors.TextSecondary, modifier = Modifier.fillMaxSize(0.45f))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The purple-to-blue heart tile used for Liked Songs. */
@Composable
fun LikedSongsCover(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    GradientCover(listOf(Color(0xFF4A1FD1), Color(0xFF8FB9E8)), modifier, shape) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

@Composable
fun RecentCover(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    GradientCover(listOf(Color(0xFF0D7347), Color(0xFF1ED760)), modifier, shape) {
        Icon(Icons.Filled.History, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

@Composable
private fun GradientCover(colors: List<Color>, modifier: Modifier, shape: Shape, icon: @Composable () -> Unit) {
    Box(
        modifier.clip(shape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** Cover for a playlist: its first song's artwork, or a music note. */
@Composable
fun PlaylistCover(firstSongArt: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) =
    Artwork(firstSongArt, modifier, shape)

private val colorCache = HashMap<String, Color>()

/** A dark, saturated colour pulled from the artwork, for backgrounds behind it. */
@Composable
fun rememberArtworkColor(url: String?, fallback: Color = WavemothColors.Elevated): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf(url?.let { colorCache[it] } ?: fallback) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || colorCache.containsKey(url)) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(128).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val swatch = withContext(Dispatchers.Default) {
            Palette.from(bitmap).generate().let { it.vibrantSwatch ?: it.dominantSwatch ?: it.mutedSwatch }
        } ?: return@LaunchedEffect
        // Darken so white text always stays readable on top.
        val picked = lerp(Color(swatch.rgb), Color.Black, 0.35f)
        colorCache[url] = picked
        color = picked
    }
    return color
}

