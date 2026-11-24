// the module store's pieces: the account pill, section titles, the big chart card tinted by its banner, a module's card, its button, and the loading and message cards

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.mochyy.mochymusic.constants.DiscordProfileKey
import com.mochyy.mochymusic.discord.DiscordProfile
import com.mochyy.mochymusic.utils.AppIcons
import com.mochyy.mochymusic.utils.rememberPreference

import com.mochyy.mochymusic.ui.component.LocalWallpaperPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedPerson
import com.mochyy.mochymusic.ui.component.containerTransformSource
import com.mochyy.mochymusic.ui.component.coverTintBackground
import com.mochyy.mochymusic.ui.theme.InterFontFamily

internal val BrowserCard = Color(0xFF2A2A2A)
internal val BrowserChipOn = Color(0xFF5A5A5A)
internal val BrowserSoft = Color.White.copy(alpha = 0.55f)
private val BrowserBone = Color(0xFF474747)
private val BrowserPill = Color(0xFF6B6B6B)
private val BrowserOnline = Color(0xFF7CFF5A)
private val CardRadius = 36.dp
private val FeaturedRadius = 32.dp
private val BrowserCardShape = RoundedCornerShape(CardRadius)
private val FeaturedShape = RoundedCornerShape(FeaturedRadius)

@Composable
internal fun browserCardColor(): Color = LocalWallpaperPalette.current?.surfaceContainer ?: BrowserCard

@Composable
internal fun browserChipOnColor(): Color = LocalWallpaperPalette.current?.chipSelected ?: BrowserChipOn

@Composable
internal fun browserBoneColor(): Color = LocalWallpaperPalette.current?.surfaceContainerHigh ?: BrowserBone

@Composable
internal fun browserPillColor(): Color = LocalWallpaperPalette.current?.surfaceContainerHigh ?: BrowserPill

/** the key a module's card and its store page share, so the page grows out of whichever card was tapped */
internal fun moduleTransitionKey(postId: String) = "module-$postId"
private val FeaturedBannerHeight = 270.dp
// how far a chart card's row climbs into the faded bottom of its banner, like the mock
private val BannerOverlap = 42.dp
// the chart card's colour under its banner keeps more of the banner's own colour than home's darker cards
private val FeaturedTint = floatArrayOf(0.35f, 0.42f, 0.5f)

/** what a module's button says: it can be added, a newer one is out, it's already in, it's paid and not theirs yet, a web page to open, or a pack to look inside */
enum class BrowserAction { Add, Update, Added, Locked, Open, View }

/** the signed-in person's avatar, with a dot that's green while they're in mochy's bar */
@Composable
internal fun AccountChip(person: FeedPerson, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (discordProfileJson) = rememberPreference(DiscordProfileKey, defaultValue = "")
    val discordAvatar = remember(discordProfileJson) {
        DiscordProfile.fromJson(discordProfileJson.takeIf { it.isNotBlank() })?.avatarUrl
    }
    val appIcon = remember(context) { AppIcons.current(context) }
    val avatarUrl = discordAvatar ?: person.avatar

    Row(
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(browserCardColor()).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val description = stringResource(R.string.module_browser_account, person.name)
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape),
            )
        } else {
            val iconBg = appIcon.background?.let { colorResource(it) } ?: browserBoneColor()
            Box(Modifier.size(30.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(appIcon.foreground),
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.38f
                        scaleY = 1.38f
                    },
                )
            }
        }
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.35f)))
        Box(Modifier.padding(end = 4.dp).size(12.dp).clip(CircleShape).background(if (person.member) BrowserOnline else BrowserSoft))
    }
}

@Composable
internal fun BrowserSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = (-0.8).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** a chart module in best from community: its banner melting into a card tinted by the banner's own colours, then its icon, name, maker and get button */
@Composable
internal fun FeaturedCard(
    module: FeedModule,
    action: BrowserAction,
    fetching: Boolean,
    onOpen: (FeedModule) -> Unit,
    onAction: (FeedModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = remember(module.banner, module.icon) { listOfNotNull(module.banner ?: module.icon) }
    Column(
        modifier
            .containerTransformSource(moduleTransitionKey(module.id), cornerRadius = FeaturedRadius, coverUrl = module.banner)
            .clip(FeaturedShape)
            .background(browserCardColor())
            .coverTintBackground(art, seed = module.id, darkening = FeaturedTint)
            .clickable { onOpen(module) },
    ) {
        BannerArt(module.banner ?: module.icon, FeaturedBannerHeight)
        ModuleRow(
            module, action, fetching, onAction, iconSize = 64, getLabel = true,
            modifier = Modifier.pullUp(BannerOverlap).padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
        )
    }
}

/** one module in the list: its icon, name and maker; the card opens its store page, the pill adds it */
@Composable
internal fun BrowserModuleCard(
    module: FeedModule,
    action: BrowserAction,
    fetching: Boolean,
    onOpen: (FeedModule) -> Unit,
    onAction: (FeedModule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .containerTransformSource(moduleTransitionKey(module.id), cornerRadius = CardRadius, coverUrl = module.banner)
            .clip(BrowserCardShape)
            .background(browserCardColor())
            .clickable { onOpen(module) },
    ) {
        ModuleRow(module, action, fetching, onAction, iconSize = 66, getLabel = false, modifier = Modifier.padding(14.dp))
    }
}

// a banner whose bottom melts into the card's tint; one offscreen pass for a still picture, as on a module's own page
@Composable
private fun BannerArt(picture: String?, height: Dp) {
    AsyncImage(
        model = picture,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val fade = Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent)
                onDrawWithContent {
                    drawContent()
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
            },
    )
}

// icon, name and who made it, with the button at the end
@Composable
private fun ModuleRow(module: FeedModule, action: BrowserAction, fetching: Boolean, onAction: (FeedModule) -> Unit, iconSize: Int, getLabel: Boolean, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ModuleLogo(module.moduleId ?: module.id, module.name, size = iconSize, art = module.icon, corner = 0.3f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(module.name, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val by = module.author?.let { author ->
                module.version?.let { stringResource(R.string.module_browser_by_version, author, it) } ?: stringResource(R.string.module_browser_by, author)
            }
            val showDownloads = com.mochyy.mochymusic.BuildConfig.STORE_COUNTS_URL.isNotBlank() || module.downloads > 0
            if (by != null || showDownloads) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    by?.let {
                        Text(
                            it,
                            color = BrowserSoft,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (showDownloads) {
                        if (by != null) {
                            Text("·", color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 13.sp)
                        }
                        androidx.compose.material3.Icon(
                            painterResource(R.drawable.download),
                            contentDescription = null,
                            tint = BrowserSoft,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            formatDownloadCount(module.downloads),
                            color = BrowserSoft,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        ActionPill(action, fetching, getLabel = getLabel) { onAction(module) }
    }
}

/** the module's button; the chart's big cards say get where the list says add */
@Composable
internal fun ActionPill(action: BrowserAction, fetching: Boolean, getLabel: Boolean = false, onClick: () -> Unit) {
    val label = when (action) {
        BrowserAction.Add -> if (getLabel) R.string.module_browser_get else R.string.module_browser_add
        BrowserAction.Update -> R.string.module_browser_update
        BrowserAction.Added -> R.string.module_browser_added
        BrowserAction.Locked -> R.string.module_browser_locked
        BrowserAction.Open -> R.string.module_browser_open
        BrowserAction.View -> R.string.module_browser_view
    }
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (action.isBright) Color.White else browserPillColor())
            .clickable(enabled = !fetching && action != BrowserAction.Added, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (fetching) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            val ink = if (action.isBright) Color.Black else Color.White.copy(alpha = 0.8f)
            Text(stringResource(label), color = ink, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

// the pills that do something stand out in white; added and locked stay grey
private val BrowserAction.isBright get() = this == BrowserAction.Add || this == BrowserAction.Update || this == BrowserAction.Open || this == BrowserAction.View

/** the grey stand-in for a card while the list is on its way */
@Composable
internal fun BrowserBoneCard(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(BrowserCardShape).background(browserCardColor()).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(browserBoneColor()))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(120.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(browserBoneColor()))
            Box(Modifier.width(80.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(browserBoneColor()))
            Box(Modifier.width(50.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(browserBoneColor()))
        }
        Box(Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(browserPillColor()))
    }
}

/** a message card in place of the list: signed out, not in the server yet, offline, and so on, with what to do about it */
@Composable
internal fun BrowserNote(
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    primary: String? = null,
    onPrimary: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(top = 26.dp).clip(RoundedCornerShape(30.dp)).background(browserCardColor()).padding(22.dp)) {
        title?.let {
            Text(it, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
        }
        Text(body, color = BrowserSoft, fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp)
        primary?.let {
            Spacer(Modifier.height(16.dp))
            PillButton(text = it, filled = true, onClick = onPrimary)
        }
        secondary?.let {
            Spacer(Modifier.height(8.dp))
            PillButton(text = it, onClick = onSecondary)
        }
    }
}


internal fun formatDownloadCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f", count / 1_000_000f).removeSuffix(".0") + "M"
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1f", count / 1_000f).removeSuffix(".0") + "K"
    else -> count.toString()
}
