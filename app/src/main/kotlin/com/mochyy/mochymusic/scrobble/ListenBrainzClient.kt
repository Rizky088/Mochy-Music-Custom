package com.mochyy.mochymusic.scrobble

import com.music.lastfm.AudioscrobblerException
import com.music.lastfm.ScrobbleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ListenBrainzClient {
    const val DefaultServerUrl = "https://api.listenbrainz.org"
    const val SettingsUrl = "https://listenbrainz.org/settings/"
    private const val UserAgent = "MochyExt/8.4.6.2 (https://github.com/Rizky088/Mochy-Music-Custom)"

    fun normalizeServer(raw: String?): String =
        raw?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: DefaultServerUrl

    suspend fun validateToken(serverUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServer(serverUrl)
        val conn = (URL("$base/1/validate-token").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Token ${token.trim()}")
            setRequestProperty("User-Agent", UserAgent)
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code == 401 || code == 403) {
            throw AudioscrobblerException(4, "Invalid ListenBrainz token")
        }
        if (code !in 200..299) {
            throw AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "ListenBrainz answered HTTP $code")
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "Invalid response from ListenBrainz")
        if (!json.optBoolean("valid", false)) {
            throw AudioscrobblerException(4, json.optString("message").ifBlank { "Invalid ListenBrainz token" })
        }
        json.optString("user_name").takeIf { it.isNotBlank() } ?: "ListenBrainz"
    }

    suspend fun submitNowPlaying(serverUrl: String, token: String, track: ScrobbleTrack) {
        val payload = JSONArray().put(
            JSONObject().put("track_metadata", trackMetadata(track)),
        )
        postListens(serverUrl, token, "playing_now", payload)
    }

    suspend fun submitListens(serverUrl: String, token: String, plays: List<Pair<ScrobbleTrack, Long>>) {
        if (plays.isEmpty()) return
        val payload = JSONArray()
        plays.forEach { (track, startedAt) ->
            payload.put(
                JSONObject()
                    .put("listened_at", startedAt)
                    .put("track_metadata", trackMetadata(track)),
            )
        }
        val type = if (plays.size == 1) "single" else "import"
        postListens(serverUrl, token, type, payload)
    }

    private fun trackMetadata(track: ScrobbleTrack): JSONObject = JSONObject().apply {
        put("artist_name", track.artist)
        put("track_name", track.track)
        if (!track.album.isNullOrBlank()) put("release_name", track.album)
        put(
            "additional_info",
            JSONObject().apply {
                put("media_player", "Mochy Ext")
                put("submission_client", "Mochy Ext")
                track.durationSeconds?.takeIf { it > 0 }?.let { put("duration_ms", it * 1000L) }
            },
        )
    }

    private suspend fun postListens(serverUrl: String, token: String, listenType: String, payload: JSONArray) =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(serverUrl)
            val bodyBytes = JSONObject()
                .put("listen_type", listenType)
                .put("payload", payload)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val conn = (URL("$base/1/submit-listens").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Authorization", "Token ${token.trim()}")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", UserAgent)
            }
            conn.outputStream.use { it.write(bodyBytes) }
            val code = conn.responseCode
            val errBody = if (code !in 200..299) {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } else {
                conn.inputStream?.close()
                ""
            }
            conn.disconnect()
            when (code) {
                in 200..299 -> Unit
                401, 403 -> throw AudioscrobblerException(9, "ListenBrainz token rejected")
                400 -> throw AudioscrobblerException(6, errBody.ifBlank { "ListenBrainz rejected payload" })
                else -> throw AudioscrobblerException(AudioscrobblerException.SERVER_ERROR, "HTTP $code")
            }
        }

    suspend fun submitLove(
        serverUrl: String,
        token: String,
        username: String?,
        artist: String,
        track: String,
        love: Boolean,
    ) = withContext(Dispatchers.IO) {
        val base = normalizeServer(serverUrl)
        val mbid = lookupRecordingMbid(base, artist, track)
        val msid = if (mbid == null && !username.isNullOrBlank()) {
            lookupRecentRecordingMsid(base, token, username, artist, track)
        } else null
        if (mbid == null && msid == null) return@withContext

        val body = JSONObject().apply {
            if (mbid != null) put("recording_mbid", mbid) else put("recording_msid", msid)
            put("score", if (love) 1 else 0)
        }.toString().toByteArray(Charsets.UTF_8)

        val conn = (URL("$base/1/feedback/recording-feedback").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Token ${token.trim()}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", UserAgent)
        }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        if (code in 200..299) conn.inputStream?.close() else conn.errorStream?.close()
        conn.disconnect()
        if (code == 401 || code == 403) {
            throw AudioscrobblerException(9, "ListenBrainz token rejected")
        }
    }

    private fun lookupRecordingMbid(base: String, artist: String, track: String): String? = runCatching {
        val encArtist = java.net.URLEncoder.encode(artist, "UTF-8")
        val encTrack = java.net.URLEncoder.encode(track, "UTF-8")
        val conn = (URL("$base/1/metadata/lookup/?artist_name=$encArtist&recording_name=$encTrack").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", UserAgent)
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val text = if (code in 200..299) conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty() else ""
        conn.disconnect()
        if (text.isBlank()) return@runCatching null
        JSONObject(text).optString("recording_mbid").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun lookupRecentRecordingMsid(
        base: String,
        token: String,
        username: String,
        artist: String,
        track: String,
    ): String? {
        val encUser = java.net.URLEncoder.encode(username.trim(), "UTF-8")
        val endpoints = listOf(
            "$base/1/user/$encUser/playing-now",
            "$base/1/user/$encUser/listens?count=5",
        )
        for (url in endpoints) {
            val found = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Authorization", "Token ${token.trim()}")
                    setRequestProperty("User-Agent", UserAgent)
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                val text = if (code in 200..299) conn.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty() else ""
                conn.disconnect()
                if (text.isBlank()) return@runCatching null
                val listens = JSONObject(text).optJSONObject("payload")?.optJSONArray("listens") ?: return@runCatching null
                for (i in 0 until listens.length()) {
                    val item = listens.optJSONObject(i) ?: continue
                    val meta = item.optJSONObject("track_metadata") ?: continue
                    val tName = meta.optString("track_name")
                    val aName = meta.optString("artist_name")
                    if (tName.equals(track, ignoreCase = true) && aName.equals(artist, ignoreCase = true)) {
                        val msid = item.optString("recording_msid").takeIf { it.isNotBlank() }
                            ?: meta.optJSONObject("mbid_mapping")?.optString("recording_mbid")?.takeIf { it.isNotBlank() }
                        if (msid != null) return@runCatching msid
                    }
                }
                null
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }
}
