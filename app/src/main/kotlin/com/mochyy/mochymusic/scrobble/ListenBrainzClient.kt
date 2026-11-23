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
    private const val UserAgent = "MochyExt/8.4.6.1 (https://github.com/Rizky088/Mochy-Music-Custom)"

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
}
