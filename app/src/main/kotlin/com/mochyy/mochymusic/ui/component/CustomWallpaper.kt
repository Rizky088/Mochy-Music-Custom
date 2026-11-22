package com.mochyy.mochymusic.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.materialkolor.score.Score
import com.mochyy.mochymusic.constants.CustomWallpaperBlurKey
import com.mochyy.mochymusic.constants.CustomWallpaperDimKey
import com.mochyy.mochymusic.constants.CustomWallpaperPathKey
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class WallpaperPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val surfaceDeep: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val chipTrack: Color,
    val chip: Color,
    val chipSelected: Color,
    val divider: Color,
    val onSurfaceVariant: Color,
    val placeholder: Color,
    val surfaceTint: Color,
)

val LocalWallpaperPalette = compositionLocalOf<WallpaperPalette?> { null }

private fun hslColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val argb = ColorUtils.HSLToColor(
        floatArrayOf(
            ((h % 360f) + 360f) % 360f,
            s.coerceIn(0f, 1f),
            l.coerceIn(0f, 1f),
        )
    )
    return Color(argb).copy(alpha = alpha)
}

private fun buildWallpaperPalette(primaryArgb: Int, secondaryArgb: Int): WallpaperPalette {
    val pHsl = FloatArray(3)
    val sHsl = FloatArray(3)
    ColorUtils.colorToHSL(primaryArgb, pHsl)
    ColorUtils.colorToHSL(secondaryArgb, sHsl)

    val h1 = pHsl[0]
    val rawS1 = pHsl[1]
    val mono = rawS1 < 0.06f
    val s1 = if (mono) rawS1 else rawS1.coerceIn(0.28f, 0.78f)

    val h2 = if (mono) h1 else if (kotlin.math.abs(sHsl[0] - h1) < 15f) (h1 + 28f) % 360f else sHsl[0]
    val s2 = if (mono) rawS1 else sHsl[1].coerceIn(0.24f, 0.72f)

    val surfSat = if (mono) 0f else (s1 * 0.58f).coerceIn(0.18f, 0.42f)
    val cardSat = if (mono) 0f else (s1 * 0.55f).coerceIn(0.20f, 0.44f)
    val accentSat = if (mono) 0f else (s1 * 0.95f).coerceIn(0.42f, 0.80f)

    val primary = hslColor(h1, accentSat, 0.78f)
    val secondary = hslColor(h2, if (mono) 0f else (s2 * 0.88f).coerceIn(0.34f, 0.74f), 0.74f)

    return WallpaperPalette(
        primary = primary,
        onPrimary = hslColor(h1, surfSat, 0.10f),
        primaryContainer = hslColor(h1, if (mono) 0f else (s1 * 0.68f).coerceIn(0.28f, 0.56f), 0.28f),
        secondary = secondary,
        onSecondary = hslColor(h2, surfSat, 0.10f),
        secondaryContainer = hslColor(h2, if (mono) 0f else (s2 * 0.60f).coerceIn(0.24f, 0.50f), 0.24f),
        surfaceDeep = hslColor(h1, surfSat, 0.065f),
        surface = hslColor(h1, surfSat, 0.105f),
        surfaceContainer = hslColor(h1, cardSat, 0.155f),
        surfaceContainerHigh = hslColor(h1, if (mono) 0f else (cardSat * 1.08f).coerceAtMost(0.48f), 0.215f),
        chipTrack = hslColor(h1, surfSat, 0.12f, alpha = 0.72f),
        chip = hslColor(h1, cardSat, 0.20f, alpha = 0.78f),
        chipSelected = hslColor(h1, if (mono) 0f else (s1 * 0.75f).coerceIn(0.32f, 0.62f), 0.38f, alpha = 0.94f),
        divider = hslColor(h1, cardSat, 0.30f, alpha = 0.55f),
        onSurfaceVariant = hslColor(h1, if (mono) 0f else (s1 * 0.35f).coerceIn(0.10f, 0.24f), 0.80f),
        placeholder = hslColor(h1, if (mono) 0f else (s1 * 0.30f).coerceIn(0.08f, 0.22f), 0.68f),
        surfaceTint = primary.copy(alpha = 0.18f),
    )
}

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
                val swatches = Palette.from(bmp)
                    .maximumColorCount(24)
                    .generate()
                    .swatches
                val popMap = swatches.associate { it.rgb to it.population }
                val scored = if (popMap.isNotEmpty()) {
                    runCatching { Score.score(popMap, 2, 0xFF4A6FA5.toInt(), true) }.getOrNull()
                } else null
                val primaryArgb = scored?.firstOrNull()
                    ?: swatches.maxByOrNull { it.population }?.rgb
                    ?: 0xFF4A6FA5.toInt()
                val secondaryArgb = scored?.getOrNull(1) ?: primaryArgb
                buildWallpaperPalette(primaryArgb, secondaryArgb)
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
    val dim by rememberPreference(CustomWallpaperDimKey, defaultValue = 0.38f)
    val blurDp by rememberPreference(CustomWallpaperBlurKey, defaultValue = 0f)
    val palette = LocalWallpaperPalette.current ?: rememberWallpaperPalette()
    val scrimColor = palette?.surfaceDeep ?: Color.Black
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

        if (paletteOverlay && palette != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to palette.primary.copy(alpha = 0.08f),
                            0.55f to palette.secondary.copy(alpha = 0.06f),
                            1.0f to scrimColor.copy(alpha = 0.16f),
                        )
                    ),
            )
        }

        if (dim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = dim.coerceIn(0f, 0.85f))),
            )
        }

        if (smoothBottomFade) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.74f to Color.Transparent,
                            0.90f to scrimColor.copy(alpha = 0.18f),
                            1.0f to scrimColor.copy(alpha = 0.38f),
                        )
                    ),
            )
        }
    }
}
