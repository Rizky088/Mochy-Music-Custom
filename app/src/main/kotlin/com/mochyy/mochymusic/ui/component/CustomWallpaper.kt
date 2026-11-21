package com.mochyy.mochymusic.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.constants.CustomWallpaperBlurKey
import com.mochyy.mochymusic.constants.CustomWallpaperDimKey
import com.mochyy.mochymusic.constants.CustomWallpaperPathKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

object CustomWallpaperStore {
    private const val Prefix = "custom_wallpaper_"

    suspend fun save(context: Context, uri: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        val newFile = File(app.filesDir, "$Prefix${System.currentTimeMillis()}.jpg")
        val ok = runCatching {
            app.contentResolver.openInputStream(uri)!!.use { input ->
                newFile.outputStream().use { input.copyTo(it) }
            }
            true
        }.getOrDefault(false)
        if (!ok) {
            newFile.delete()
            return@withContext
        }
        var oldPath: String? = null
        app.dataStore.edit { prefs ->
            oldPath = prefs[CustomWallpaperPathKey]
            prefs[CustomWallpaperPathKey] = newFile.absolutePath
        }
        oldPath?.let { deleteOwned(app, it) }
    }

    suspend fun clear(context: Context) = withContext(NonCancellable + Dispatchers.IO) {
        val app = context.applicationContext
        var oldPath: String? = null
        app.dataStore.edit { prefs ->
            oldPath = prefs[CustomWallpaperPathKey]
            prefs.remove(CustomWallpaperPathKey)
        }
        oldPath?.let { deleteOwned(app, it) }
    }

    private fun deleteOwned(context: Context, path: String) {
        val f = File(path)
        if (f.parentFile == context.filesDir && f.name.startsWith(Prefix)) f.delete()
    }
}

@Composable
fun CustomWallpaperLayer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val path by rememberPreference(CustomWallpaperPathKey, defaultValue = "")
    if (path.isBlank()) return
    val dim by rememberPreference(CustomWallpaperDimKey, defaultValue = 0.45f)
    val blurDp by rememberPreference(CustomWallpaperBlurKey, defaultValue = 0f)
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
        if (dim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f))),
            )
        }
    }
}
