// LibraryMoreTabs.kt
//
// The Library's History and Genres tabs: the last few days of what you played, and the genres of
// everything you keep or play, each with a way into its full page.

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.component.LocalWallpaperPalette
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.utils.HiddenSong

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.EventWithSong
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.screens.historyDayHeading
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import com.mochyy.mochymusic.ui.screens.search.SearchLoadingDots
import com.mochyy.mochymusic.viewmodels.LocalGenre
import com.mochyy.mochymusic.viewmodels.localGenreRoute
import java.time.LocalDate

private const val HistoryDays = 3
private const val HistoryPerDay = 8

/** the last few days of plays, a handful each, under a card that opens all of History */
fun LazyListScope.libraryHistoryTab(
    events: Map<LocalDate, List<EventWithSong>>?,
    nowPlaying: State<MediaMetadata?>?,
    isPlaying: State<Boolean>?,
    player: PlayerConnection?,
    navController: NavController,
) {
    if (events == null) {
        item(key = "history_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (events.isEmpty()) {
        item(key = "history_empty") { LibraryEmptyState(text = stringResource(R.string.lib_history_empty)) }
        return
    }
    val days = events.entries.take(HistoryDays).map { (day, plays) -> day to plays.take(HistoryPerDay) }
    // a tap plays what this tab shows, from that song on, like History does with all of it
    val shown = days.flatMap { (_, plays) -> plays.map { it.song } }
    item(key = "history_all") {
        LibraryWideCard(
            title = stringResource(R.string.lib_history_all),
            count = null,
            covers = shown.take(4).map { it.song.thumbnailUrl },
            onClick = { navController.navigate("history") },
            modifier = Modifier.padding(horizontal = SearchHorizontalPadding),
        )
        Spacer(Modifier.height(8.dp))
    }
    days.forEach { (day, plays) ->
        item(key = "history_day_$day", contentType = "historyDay") {
            Spacer(Modifier.height(14.dp))
            LibrarySectionTitle(historyDayHeading(day))
            Spacer(Modifier.height(6.dp))
        }
        items(plays, key = { "history_${it.event.id}" }, contentType = { "historyRow" }) { play ->
            val song = play.song
            val active = song.id == nowPlaying?.value?.id
            LibraryListRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name }.ifBlank { null },
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = active,
                isPlaying = active && isPlaying?.value == true,
                songId = song.id,
                onClick = {
                    if (active) {
                        player?.togglePlayPause()
                    } else {
                        player?.playQueue(
                            ListQueue(items = shown.map { it.toMediaItem() }, startIndex = shown.indexOf(song).coerceAtLeast(0)),
                        )
                    }
                },
                onLongClick = { navController.navigate("history") },
            )
        }
    }
}

/** the genres of your songs (tags first, the artist's genre otherwise), one row each like a playlist, each opening its own page */
fun LazyListScope.libraryGenresTab(genres: List<LocalGenre>?, navController: NavController) {
    if (genres == null) {
        item(key = "genres_loading") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { SearchLoadingDots() }
        }
        return
    }
    if (genres.isEmpty()) {
        item(key = "genres_empty") { LibraryEmptyState(text = stringResource(R.string.lib_genres_local_empty)) }
        return
    }
    item(key = "genres_top") { Spacer(Modifier.height(4.dp)) }
    items(genres, key = { "genre_${it.name.lowercase()}" }, contentType = { "genreRow" }) { genre ->
        val open = { navController.navigate(localGenreRoute(genre.name)) }
        LibraryListRow(
            title = genre.name,
            subtitle = pluralStringResource(R.plurals.n_song, genre.songs.size, genre.songs.size),
            thumbnailUrl = genre.songs.firstNotNullOfOrNull { it.song.thumbnailUrl?.takeIf(String::isNotEmpty) },
            onClick = open,
            onLongClick = open,
        )
    }
}

/** blocked / hidden songs, with one-tap Unhide so people can easily restore them */
fun LazyListScope.libraryHiddenTab(
    hiddenSongs: List<HiddenSong>,
    librarySongs: List<Song>,
    query: String,
    onUnhide: (String) -> Unit,
    onUnhideAll: () -> Unit,
) {
    val byId = librarySongs.associateBy { it.id }
    val resolved = hiddenSongs.map { item ->
        val dbSong = byId[item.id]
        HiddenSong(
            id = item.id,
            title = item.title.ifBlank { dbSong?.song?.title.orEmpty() }.ifBlank { item.id },
            artist = item.artist.ifBlank { dbSong?.artists?.joinToString(", ") { it.name }.orEmpty() },
            thumbnailUrl = item.thumbnailUrl ?: dbSong?.song?.thumbnailUrl,
            hiddenAt = item.hiddenAt,
        )
    }
    val filtered = if (query.isBlank()) {
        resolved
    } else {
        resolved.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }

    if (resolved.isEmpty()) {
        item(key = "hidden_empty") {
            LibraryEmptyState(text = "No hidden music yet.\nUse \"Don't recommend\" on any song to hide it from playback and recommendations.")
        }
        return
    }

    item(key = "hidden_unhide_all") {
        LibraryManagePill(text = "Unhide all (${resolved.size})", onClick = onUnhideAll)
        Spacer(Modifier.height(18.dp))
    }

    if (filtered.isEmpty()) {
        item(key = "hidden_no_match") {
            LibraryEmptyState(text = "No hidden songs match \"$query\".")
        }
        return
    }

    item(key = "hidden_header") {
        LibraryCapsLabel("Hidden music · ${filtered.size}")
        Spacer(Modifier.height(8.dp))
    }

    items(filtered, key = { "hidden_${it.id}" }, contentType = { "hiddenRow" }) { song ->
        val wp = LocalWallpaperPalette.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SearchHorizontalPadding, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(wp?.surfaceContainer?.copy(alpha = 0.72f) ?: Color(0xFF1A1A1D))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(wp?.surfaceContainerHigh ?: Color(0xFF28282C)),
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = song.title,
                    color = SearchColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        color = SearchColors.Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(wp?.chipSelected ?: Color(0xFF3A3A3E))
                    .clickable { onUnhide(song.id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Unhide",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
