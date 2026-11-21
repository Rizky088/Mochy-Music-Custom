package com.mochyy.mochymusic.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.constants.CustomWallpaperBlurKey
import com.mochyy.mochymusic.constants.CustomWallpaperDimKey
import com.mochyy.mochymusic.constants.CustomWallpaperPathKey
import com.mochyy.mochymusic.ui.theme.extractGradientColors
import com.mochyy.mochymusic.ui.theme.extractThemeColor
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class WallpaperPalette(
    val primary: Color,
    val secondary: Color,
    val surfaceTint: Color,
)

@Composable
fun rememberWallpaperPalette(): WallpaperPalette? {
    val path by rememberPreference(CustomWallpaperPathKey, defaultValue = "")
    if (path.isBlank()) return null

    val palette by produceState<WallpaperPalette?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || file.length() <= 0L) return@withContext null
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            try {
                val themeCol = runCatching { bmp.extractThemeColor() }.getOrDefault(Color(0xFF4A6FA5))
                val grads = runCatching { bmp.extractGradientColors() }.getOrDefault(listOf(themeCol, Color(0xFF141824)))
                val sec = grads.getOrNull(1) ?: themeCol
                val safePrimary = if (themeCol.luminance() < 0.08f) {
                    themeCol.copy(
                        red = (themeCol.red + 0.25f).coerceAtMost(1f),
                        green = (themeCol.green + 0.22f).coerceAtMost(1f),
                        blue = (themeCol.blue + 0.30f).coerceAtMost(1f),
                    )
                } else themeCol
                val safeSec = if (sec.luminance() < 0.06f) {
                    sec.copy(
                        red = (sec.red + 0.20f).coerceAtMost(1f),
                        green = (sec.green + 0.18f).coerceAtMost(1f),
                        blue = (sec.blue + 0.25f).coerceAtMost(1f),
                    )
                } else sec
                WallpaperPalette(
                    primary = safePrimary.copy(alpha = 1f),
                    secondary = safeSec.copy(alpha = 1f),
                    surfaceTint = safePrimary.copy(alpha = 0.22f),
                )
            } finally {
                bmp.recycle()
            }
        }
    }
    return palette
}

@Composable
fun CustomWallpaperLayer(
    enabled: Boolean,
    smoothBottomFade: Boolean = false,
    paletteOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val path by rememberPreference(CustomWallpaperPathKey, defaultValue = "")
    if (path.isBlank()) return
    val dim by rememberPreference(CustomWallpaperDimKey, defaultValue = 0.42f)
    val blurDp by rememberPreference(CustomWallpaperBlurKey, defaultValue = 0f)
    val palette = if (paletteOverlay) rememberWallpaperPalette() else null
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(path))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurDp > 0.5f) Modifier.blur(blurDp.dp) else Modifier),
        )
        if (palette != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.20f),
                                palette.secondary.copy(alpha = 0.26f),
                                Color.Black.copy(alpha = 0.35f),
                            )
                        )
                    ),
            )
        }
        if (dim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f))),
            )
        }
        if (smoothBottomFade) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.85f to Color.Black.copy(alpha = 0.22f),
                            1.0f to Color.Black.copy(alpha = 0.48f),
                        )
                    ),
            )
        }
    }
}
