// CustomHomeSections.kt

package com.mochyy.mochymusic.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import sh.calvin.reorderable.ReorderableCollectionItemScope

internal sealed class CustomHomeBlock(val id: String) {
    data object Hero : CustomHomeBlock("hero")
    data object StartListening : CustomHomeBlock("start_listening")
    data object HopIn : CustomHomeBlock("hop_in")
    data class Standard(val section: HomeSection) : CustomHomeBlock(section.id)
}

internal data class CustomHomeCatalogEntry(
    val id: String,
    val title: String,
    val subtitle: String,
)

internal const val CustomHomeHiddenNone = "__none__"
internal val DefaultCustomHomeHidden: Set<String> = setOf("start_listening", "hop_in")

internal val CustomHomeCatalog: List<CustomHomeCatalogEntry> = listOf(
    CustomHomeCatalogEntry("hero", "Hero Carousel", "Featured banner and quick recommendations"),
    CustomHomeCatalogEntry("start_listening", "Start Listening", "Compact song grid at the top"),
    CustomHomeCatalogEntry("hop_in", "Hop in", "Quick-access 2-row local grid"),
    CustomHomeCatalogEntry("account_playlists", "Your playlists", "Playlists you created or saved"),
    CustomHomeCatalogEntry("recently_played", "Recently Played", "Songs, albums and playlists played recently"),
    CustomHomeCatalogEntry("pinned", "Pinned", "Items pinned from your library"),
    CustomHomeCatalogEntry("most_played", "Most Played", "Your top ranked songs"),
    CustomHomeCatalogEntry("from_the_community", "From the Community", "Community playlists"),
    CustomHomeCatalogEntry("history", "History", "Recent listening history"),
    CustomHomeCatalogEntry("live_shows", "Live Shows", "Live performances and sessions"),
    CustomHomeCatalogEntry("artist_list", "Artist List", "Similar artist cards"),
    CustomHomeCatalogEntry("dont_forget_these_songs", "Don't Forget These Songs", "Older favorites to revisit"),
    CustomHomeCatalogEntry("music_videos", "Music Videos", "Recommended music videos"),
    CustomHomeCatalogEntry("all_time_hits", "All Time Hits", "Popular tracks and hits"),
    CustomHomeCatalogEntry("categories", "Recommended Shelves", "Genre & 'More like' rows"),
    CustomHomeCatalogEntry("youtube_feed", "YouTube Feed Sections", "Additional feed shelves from YouTube Music"),
)

internal fun parseCustomHomeOrder(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

internal fun serializeCustomHomeOrder(order: List<String>): String =
    order.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

internal fun parseCustomHomeHidden(raw: String): Set<String> = when {
    raw.isBlank() -> DefaultCustomHomeHidden
    raw == CustomHomeHiddenNone -> emptySet()
    else -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

internal fun serializeCustomHomeHidden(hidden: Set<String>): String =
    if (hidden.isEmpty()) CustomHomeHiddenNone else hidden.sorted().joinToString(",")

internal fun isCustomBlockHidden(block: CustomHomeBlock, hidden: Set<String>): Boolean {
    if (block.id in hidden) return true
    if (block is CustomHomeBlock.Standard) {
        when (block.section) {
            is HomeSection.Category -> if ("categories" in hidden) return true
            is HomeSection.HomePageSection -> if ("youtube_feed" in hidden) return true
            else -> Unit
        }
    }
    return false
}

internal fun orderCustomHomeBlocks(
    available: List<CustomHomeBlock>,
    savedOrder: List<String>,
    hidden: Set<String>,
): List<CustomHomeBlock> {
    val visible = available.filterNot { isCustomBlockHidden(it, hidden) }
    if (savedOrder.isEmpty()) return visible
    val byId = visible.associateBy { it.id }
    val picked = ArrayList<CustomHomeBlock>(visible.size)
    val seen = HashSet<String>(visible.size)
    for (id in savedOrder) {
        val block = byId[id] ?: continue
        if (seen.add(block.id)) picked += block
    }
    for (block in visible) {
        if (seen.add(block.id)) picked += block
    }
    return picked
}

private val EditCardShape = RoundedCornerShape(22.dp)
private val DragGlowBorder = Color(0xFF7DD3FC)
private val DragGlowFill = Color(0xFF38BDF8)

@Composable
internal fun ReorderableCollectionItemScope.CustomHomeEditCard(
    isDragging: Boolean,
    onPickUp: () -> Unit,
    onDrop: () -> Unit,
    content: @Composable () -> Unit,
) {
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "customHomeCardLift",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .graphicsLayer {
                val scale = 1f + 0.02f * lift
                scaleX = scale
                scaleY = scale
                shadowElevation = 18.dp.toPx() * lift
                shape = EditCardShape
                clip = false
            }
            .clip(EditCardShape)
            .background(
                if (isDragging) DragGlowFill.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.055f)
            )
            .drawWithContent {
                drawContent()
                val radius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
                if (isDragging) {
                    drawRoundRect(
                        color = DragGlowBorder.copy(alpha = 0.92f),
                        cornerRadius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                } else {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        cornerRadius = radius,
                        style = Stroke(
                            width = 1.25.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 11f), 0f),
                        ),
                    )
                }
            }
            .longPressDraggableHandle(
                onDragStarted = { onPickUp() },
                onDragStopped = { onDrop() },
            )
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }

        // 3-line drag handle shown ONLY in edit mode
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isDragging) Color.White.copy(alpha = 0.24f)
                    else Color.White.copy(alpha = 0.10f)
                )
                .draggableHandle(
                    onDragStarted = { onPickUp() },
                    onDragStopped = { onDrop() },
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_drag_handle),
                contentDescription = "Drag section",
                tint = Color.White.copy(alpha = if (isDragging) 1f else 0.78f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun CustomHeroEditPreview(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 58.dp, top = 4.dp),
    ) {
        Text(
            text = "Hero Carousel",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "Featured banner" },
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CustomHomeEditTopActions(
    onDone: () -> Unit,
    onAddSection: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onDone)
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Done",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAddSection)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add section",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Add\nSection",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
internal fun CustomHomeEditChip(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit custom home",
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "Edit",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color.White,
        )
    }
}

@Composable
internal fun CustomHomeAddSectionSheet(
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Homepage sections",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Choose which sections appear on your custom Home",
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onReset)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Reset",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            CustomHomeCatalog.forEach { entry ->
                val enabled = entry.id !in hidden
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onToggle(entry.id, !enabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                        Text(
                            text = entry.subtitle,
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    AppSwitch(
                        checked = enabled,
                        onCheckedChange = { onToggle(entry.id, it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onDismiss),
        ) {
            Text(
                text = "Done",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black,
            )
        }
    }
}

@Composable
internal fun ReorderableCollectionItemScope.CustomHomeCompactEditCard(
    title: String,
    subtitle: String,
    thumbnails: List<String>,
    isFirst: Boolean,
    isDragging: Boolean,
    onMoveToTop: () -> Unit,
    onPickUp: () -> Unit,
    onDrop: () -> Unit,
) {
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "customHomeCompactLift",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .graphicsLayer {
                val scale = 1f + 0.025f * lift
                scaleX = scale
                scaleY = scale
                shadowElevation = 18.dp.toPx() * lift
                shape = EditCardShape
                clip = false
            }
            .clip(EditCardShape)
            .background(
                if (isDragging) DragGlowFill.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.075f)
            )
            .drawWithContent {
                drawContent()
                val radius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
                if (isDragging) {
                    drawRoundRect(
                        color = DragGlowBorder.copy(alpha = 0.95f),
                        cornerRadius = radius,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                } else {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.28f),
                        cornerRadius = radius,
                        style = Stroke(
                            width = 1.25.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                        ),
                    )
                }
            }
            .longPressDraggableHandle(
                onDragStarted = { onPickUp() },
                onDragStopped = { onDrop() },
            )
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (thumbnails.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                thumbnails.take(2).forEach { url ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!isFirst) {
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onMoveToTop)
                    .padding(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move to top",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Top",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // 3-line drag handle shown ONLY in edit mode — immediate drag without waiting for long press
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (isDragging) Color.White.copy(alpha = 0.26f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .draggableHandle(
                    onDragStarted = { onPickUp() },
                    onDragStopped = { onDrop() },
                ),
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_drag_handle),
                contentDescription = "Drag section",
                tint = Color.White.copy(alpha = if (isDragging) 1f else 0.82f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
