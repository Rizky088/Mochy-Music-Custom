package com.mochyy.mochymusic.ui.screens.gesture

import android.graphics.Typeface
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AppFontModeKey
import com.mochyy.mochymusic.constants.CustomFontNameKey
import com.mochyy.mochymusic.constants.CustomFontPathKey
import com.mochyy.mochymusic.constants.HideBottomNavScrimKey
import com.mochyy.mochymusic.constants.HideHomeTopGlowKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.AppFontMode
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference
import java.io.File

@Composable
internal fun AppFontSection() {
    val context = LocalContext.current
    val (fontModeRaw, setFontMode) = rememberPreference(AppFontModeKey, defaultValue = AppFontMode.DEFAULT.name)
    val (customPath, setCustomPath) = rememberPreference(CustomFontPathKey, defaultValue = "")
    val (customName, setCustomName) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val mode = AppFontMode.entries.firstOrNull { it.name == fontModeRaw } ?: AppFontMode.DEFAULT
    var importing by remember { mutableStateOf(false) }

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        val appCtx = context.applicationContext
        val oldPath = customPath
        Thread {
            val displayName = runCatching {
                appCtx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "custom_font.ttf"

            val ext = displayName.substringAfterLast('.', "").lowercase().takeIf { it in setOf("ttf", "otf", "ttc") } ?: "ttf"
            val outFile = File(appCtx.filesDir, "custom_app_font_${System.currentTimeMillis()}.$ext")
            val loadedTypeface = runCatching {
                appCtx.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Cannot read font")
                if (!outFile.isFile || outFile.length() == 0L) error("Empty font file")
                Typeface.createFromFile(outFile)
            }.getOrNull()

            if (loadedTypeface != null) {
                runCatching {
                    val prev = File(oldPath)
                    if (oldPath.isNotBlank() && prev.absolutePath != outFile.absolutePath &&
                        prev.parentFile == appCtx.filesDir && prev.name.startsWith("custom_app_font_")
                    ) {
                        prev.delete()
                    }
                }
            } else {
                outFile.delete()
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                importing = false
                if (loadedTypeface != null) {
                    setCustomPath(outFile.absolutePath)
                    setCustomName(displayName)
                    setFontMode(AppFontMode.CUSTOM.name)
                    Toast.makeText(appCtx, "Applied font: $displayName", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "Invalid font file (choose a .ttf or .otf file)", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    Column {
        Text(
            text = stringResource(R.string.customise_app_font),
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.05).em,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        LabCard(padding = 20.dp) {
            Text(
                text = "Choose Mochy's default Inter font, your device's system font, or import your own .ttf / .otf file.",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = LabColors.Secondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FontChoiceChip(
                    label = "Default",
                    selected = mode == AppFontMode.DEFAULT,
                    onClick = { setFontMode(AppFontMode.DEFAULT.name) },
                    modifier = Modifier.weight(1f),
                )
                FontChoiceChip(
                    label = "System",
                    selected = mode == AppFontMode.SYSTEM,
                    onClick = { setFontMode(AppFontMode.SYSTEM.name) },
                    modifier = Modifier.weight(1f),
                )
                FontChoiceChip(
                    label = "Custom",
                    selected = mode == AppFontMode.CUSTOM,
                    onClick = {
                        if (customPath.isNotBlank() && File(customPath).isFile) {
                            setFontMode(AppFontMode.CUSTOM.name)
                        } else {
                            fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LabColors.Row)
                        .clickable(enabled = !importing) {
                            fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*"))
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = when {
                            importing -> "Loading font..."
                            customName.isNotBlank() -> "Import font ($customName)"
                            else -> "Pick .ttf / .otf font file"
                        },
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (customPath.isNotBlank()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(LabColors.Row)
                            .clickable {
                                runCatching { File(customPath).delete() }
                                setCustomPath("")
                                setCustomName("")
                                if (mode == AppFontMode.CUSTOM) setFontMode(AppFontMode.DEFAULT.name)
                                Toast.makeText(context, "Custom font removed", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "Clear",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else LabColors.Row)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (selected) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun EdgeOverlaysSection() {
    val (hideTopGlow, setHideTopGlow) = rememberPreference(HideHomeTopGlowKey, defaultValue = false)
    val (hideBottomScrim, setHideBottomScrim) = rememberPreference(HideBottomNavScrimKey, defaultValue = false)

    Column {
        Text(
            text = stringResource(R.string.customise_edge_overlays),
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.05).em,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        LabCard(padding = 20.dp) {
            OverlayToggleItem(
                title = "Home top cover glow",
                subtitle = "Soft color wash at the top of the Home screen sampled from your recent covers",
                checked = !hideTopGlow,
                onCheckedChange = { show -> setHideTopGlow(!show) },
            )
            Spacer(Modifier.height(10.dp))
            OverlayToggleItem(
                title = "Bottom bar dark fade",
                subtitle = "Dark gradient behind the floating mini player and navigation pill",
                checked = !hideBottomScrim,
                onCheckedChange = { show -> setHideBottomScrim(!show) },
            )
        }
    }
}

@Composable
private fun OverlayToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LabColors.Row)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
            )
            Text(
                text = subtitle,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = LabColors.Secondary,
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
