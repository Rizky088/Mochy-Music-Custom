// ArtistListCard.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.ArtistGroup
import com.music.innertube.models.YTItem

@Composable
fun ArtistListCard(
    group: ArtistGroup,
    onClick: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tintCovers = remember(group) {
        listOfNotNull(group.artistThumbnailUrl) + group.items.take(2).mapNotNull { it.thumbnail }
    }
    val items = group.items.take(5)

    Box(
        modifier = modifier
            .width(276.dp)
            .height(308.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .coverTintBackground(tintCovers, seed = group.artistName)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (group.artistThumbnailUrl != null) {
                        AsyncImage(
                            model = group.artistThumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        AlbumGradient(thumbnailUrl = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pc_hero_similar_to),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Text(
                        text = group.artistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        fontSize = 20.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Exact 236dp x 198dp content box: every cover fits cleanly inside card bounds
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (items.size) {
                    0 -> Unit
                    1 -> ArtistPreviewTile(
                        item = items[0],
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onItemClick(items[0]) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    2 -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items.forEach { item ->
                            ArtistPreviewTile(
                                item = item,
                                shape = RoundedCornerShape(16.dp),
                                onClick = { onItemClick(item) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                    3 -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        ArtistPreviewTile(
                            item = items[0],
                            shape = RoundedCornerShape(16.dp),
                            onClick = { onItemClick(items[0]) },
                            modifier = Modifier.weight(1.35f).fillMaxHeight(),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            ArtistPreviewTile(
                                item = items[1],
                                shape = RoundedCornerShape(14.dp),
                                onClick = { onItemClick(items[1]) },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                            ArtistPreviewTile(
                                item = items[2],
                                shape = RoundedCornerShape(14.dp),
                                onClick = { onItemClick(items[2]) },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                    4 -> Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        for (row in items.chunked(2)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            ) {
                                row.forEach { item ->
                                    ArtistPreviewTile(
                                        item = item,
                                        shape = RoundedCornerShape(14.dp),
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                    else -> Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        ) {
                            items.take(3).forEach { item ->
                                ArtistPreviewTile(
                                    item = item,
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        ) {
                            items.drop(3).take(2).forEach { item ->
                                ArtistPreviewTile(
                                    item = item,
                                    shape = CircleShape,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier.size(112.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistPreviewTile(
    item: YTItem,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        AlbumGradient(thumbnailUrl = item.thumbnail, modifier = Modifier.fillMaxSize())
        item.thumbnail?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        }
    }
}
