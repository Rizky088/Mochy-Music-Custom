package com.music.directlyrics

import android.util.Base64
import android.util.Xml
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

/** petitlyrics: word/character-synced wsy xml (strong japanese/anime catalog) with plain text fallback */
object PetitLyrics {

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> = runCatching {
        val rawTitle = title.trim()
        check(rawTitle.isNotEmpty()) { "Missing title" }
        val leadArtist = artist.substringBefore(",").substringBefore("&").substringBefore(" feat.").trim()

        // 1. Word-synced WSY (lyricsType = 3)
        findSong(rawTitle, artist, leadArtist, lyricsType = 3)?.let { song ->
            val decoded = decodeBase64(song.lyricsData)
            if (decoded != null && decoded.contains("<wsy>")) {
                val lines = parseWsy(decoded)
                if (lines.hasLyrics()) return@runCatching lines.toLrc()
            }
        }

        // 2. Fallback to plain lyrics (lyricsType = 1)
        findSong(rawTitle, artist, leadArtist, lyricsType = 1)?.let { song ->
            val plain = decodeBase64(song.lyricsData)?.replace("\r\n", "\n")?.trim()
            if (!plain.isNullOrBlank() && !plain.startsWith("MHDROBJT")) {
                return@runCatching plain
            }
        }

        throw IllegalStateException("No matching PetitLyrics track")
    }

    private data class Song(
        val title: String,
        val artist: String,
        val lyricsData: String,
    )

    private suspend fun findSong(title: String, fullArtist: String, leadArtist: String, lyricsType: Int): Song? {
        val stripped = title.replace(Decorations, "").trim()
        val attempts = buildList {
            if (leadArtist.isNotBlank()) add(mapOf("key_title" to title, "key_artist" to leadArtist))
            if (stripped.isNotBlank() && !stripped.equals(title, ignoreCase = true) && leadArtist.isNotBlank()) {
                add(mapOf("key_title" to stripped, "key_artist" to leadArtist))
            }
            add(mapOf("key_title" to title))
        }

        for (params in attempts) {
            val songs = fetch(params, lyricsType).filter { it.lyricsData.isNotBlank() }
            if (songs.isEmpty()) continue
            val matched = songs.map { s ->
                TrackCandidate(
                    titles = listOf(s.title),
                    artists = listOf(s.artist),
                    durationSec = 0,
                    ref = s,
                )
            }.bestMatch(title, fullArtist, 0)
            if (matched != null) return matched
        }
        return null
    }

    private suspend fun fetch(extra: Map<String, String>, lyricsType: Int): List<Song> = runCatching {
        val form = buildMap {
            putAll(extra)
            put("lyricsType", lyricsType.toString())
            put("terminalType", "10")
            put("clientAppId", ClientAppId)
            put("maxCount", "5")
        }.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

        val xml = lyricsHttp.post(ApiUrl) {
            setBody(TextContent(form, ContentType.Application.FormUrlEncoded))
        }.bodyAsText()
        parseResponseXml(xml)
    }.getOrDefault(emptyList())

    private fun parseResponseXml(xml: String): List<Song> = runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<Song>()
        var inSong = false
        var tag: String? = null
        var title = ""
        var artist = ""
        var data = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (tag == "song") {
                        inSong = true
                        title = ""; artist = ""; data = ""
                    }
                }
                XmlPullParser.TEXT -> if (inSong) {
                    when (tag) {
                        "title" -> title += parser.text
                        "artist" -> artist += parser.text
                        "lyricsData" -> data += parser.text
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "song" && inSong) {
                        inSong = false
                        out += Song(title.trim(), artist.trim(), data.trim())
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        out
    }.getOrDefault(emptyList())

    private fun parseWsy(wsyXml: String): List<TimedLine> = runCatching {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(wsyXml))
        val lines = mutableListOf<TimedLine>()

        var inLine = false
        var inWord = false
        var tag: String? = null
        val lineText = StringBuilder()
        val words = mutableListOf<TimedWord>()

        var wStart = 0L
        var wEnd = 0L
        val wText = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "line" -> {
                            inLine = true
                            lineText.clear()
                            words.clear()
                        }
                        "word" -> if (inLine) {
                            inWord = true
                            wStart = 0L
                            wEnd = 0L
                            wText.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val txt = parser.text.orEmpty()
                    if (inWord) {
                        when (tag) {
                            "starttime" -> wStart = txt.trim().toLongOrNull() ?: wStart
                            "endtime" -> wEnd = txt.trim().toLongOrNull() ?: wEnd
                            "wordstring" -> wText.append(txt)
                        }
                    } else if (inLine && tag == "linestring") {
                        lineText.append(txt)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "word" -> if (inWord) {
                            inWord = false
                            val wt = wText.toString()
                            if (wt.isNotEmpty()) {
                                val safeEnd = if (wEnd > wStart) wEnd else wStart + 60L
                                words += TimedWord(wt, wStart, safeEnd)
                            }
                        }
                        "line" -> if (inLine) {
                            inLine = false
                            val full = lineText.toString().trim().ifEmpty { words.joinToString("") { it.text }.trim() }
                            val start = words.firstOrNull()?.startMs
                            if (full.isNotEmpty() && start != null) {
                                lines += TimedLine(startMs = start, text = full, words = words.toList())
                            }
                        }
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        lines
    }.getOrDefault(emptyList())

    private fun decodeBase64(b64: String): String? = runCatching {
        Base64.decode(b64.trim(), Base64.DEFAULT).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private const val ApiUrl = "https://p1.petitlyrics.com/api/GetPetitLyricsData.php"
    private const val ClientAppId = "p1110417"
    private val Decorations = Regex("""\s*[(（\[【「『《].*?[)）\]】」』》]""")
}
