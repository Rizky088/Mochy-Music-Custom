package com.music.directlyrics

import com.music.directlyrics.models.MegalobizSearchResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.math.abs
import kotlin.math.roundToInt

/** megalobiz public api v1: community line-synced lrc with manual syncs preferred */
object Megalobiz {

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> = runCatching {
        val rawTitle = title.trim()
        check(rawTitle.isNotEmpty()) { "Missing title" }
        val leadArtist = artist.substringBefore(",").substringBefore("&").substringBefore(" feat.").trim()

        var hits = search(title = rawTitle, artist = leadArtist.ifBlank { null }, query = null, duration = duration)
        if (hits.isEmpty()) {
            hits = search(title = null, artist = null, query = "$rawTitle $leadArtist".trim(), duration = duration)
        }
        check(hits.isNotEmpty()) { "No matching Megalobiz lyrics" }

        // prefer manual syncs and tighter duration matches when scores tie
        val ordered = hits.sortedWith(
            compareByDescending<MegalobizSearchResponse.Hit> { it.sync.equals("manual", ignoreCase = true) }
                .thenBy { it.durationDiff?.let(::abs) ?: 999.0 }
                .thenByDescending { it.score }
        )

        val picked = ordered.map { hit ->
            TrackCandidate(
                titles = listOf(hit.title),
                artists = listOf(hit.artist),
                durationSec = hit.duration?.roundToInt() ?: duration.coerceAtLeast(0),
                ref = hit,
            )
        }.bestMatch(rawTitle, artist, duration)
            ?: ordered.firstOrNull { it.score >= 0.85 && (it.durationDiff == null || abs(it.durationDiff) <= 4.0) }
            ?: throw IllegalStateException("No matching Megalobiz lyrics")

        val lrcUrl = picked.lrcUrl.ifBlank { "$ApiBase/lrc/${picked.id}?format=lrc" }
        val rawLrc = lyricsHttp.get(lrcUrl).bodyAsText()
        val cleaned = cleanLrc(rawLrc)
        check(parseLrc(cleaned).hasLyrics()) { "Megalobiz returned empty lyrics" }
        cleaned
    }

    private suspend fun search(
        title: String?,
        artist: String?,
        query: String?,
        duration: Int,
    ): List<MegalobizSearchResponse.Hit> = runCatching {
        lyricsHttp.get("$ApiBase/search") {
            if (!query.isNullOrBlank()) {
                parameter("q", query)
            } else {
                if (!title.isNullOrBlank()) parameter("title", title)
                if (!artist.isNullOrBlank()) parameter("artist", artist)
            }
            if (duration > 0) parameter("duration", duration)
            parameter("limit", SearchLimit)
        }.body<MegalobizSearchResponse>().results
    }.getOrDefault(emptyList())

    private fun cleanLrc(raw: String): String =
        raw.replace("\r\n", "\n")
            .lineSequence()
            .filterNot { line ->
                val low = line.trim().lowercase()
                low.startsWith("[by:") || low.contains("megalobiz.com")
            }
            .joinToString("\n")
            .trim()

    private const val ApiBase = "https://api.megalobiz.com/api/public/v1"
    private const val SearchLimit = 10
}
