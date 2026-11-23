// SoundCloudAccount.kt

package com.mochyy.mochymusic.importer.account

import com.mochyy.mochymusic.importer.ImportedTrack
import com.mochyy.mochymusic.importer.array
import com.mochyy.mochymusic.importer.dateMs
import com.mochyy.mochymusic.importer.idString
import com.mochyy.mochymusic.importer.long
import com.mochyy.mochymusic.importer.obj
import com.mochyy.mochymusic.importer.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val API = "https://api-v2.soundcloud.com"
private const val SERVICE = "SoundCloud"
private const val LIKED_ID = "soundcloud:likes"
private const val TRACK_BATCH = 50
private const val MAX_PAGES = 60

private val HTML_CLIENT_ID_REGEX = Regex(""""clientId"\s*:\s*"([A-Za-z0-9]{16,})"""")
private val JS_CLIENT_ID_REGEX = Regex("""client_id\s*[:=]\s*["']([A-Za-z0-9]{20,40})["']""")
private val SCRIPT_SRC_REGEX = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")

@Volatile
private var cachedScClientId: String? = null

private suspend fun resolveScClientId(forceRefresh: Boolean = false): String {
    if (!forceRefresh) cachedScClientId?.let { return it }
    val home = AccountHttp.get("https://soundcloud.com/", mapOf("User-Agent" to DESKTOP_USER_AGENT), SERVICE)
    if (home.status in 200..299) {
        HTML_CLIENT_ID_REGEX.find(home.body)?.groupValues?.getOrNull(1)?.let { found ->
            cachedScClientId = found
            return found
        }
        val scripts = SCRIPT_SRC_REGEX.findAll(home.body).map { it.groupValues[1] }.toList().asReversed()
        for (src in scripts.take(8)) {
            val js = AccountHttp.get(src, mapOf("User-Agent" to DESKTOP_USER_AGENT), SERVICE)
            if (js.status !in 200..299) continue
            JS_CLIENT_ID_REGEX.find(js.body)?.groupValues?.getOrNull(1)?.let { found ->
                cachedScClientId = found
                return found
            }
        }
    }
    return cachedScClientId ?: throw AccountImportException("Couldn't read SoundCloud's web session. Try again in a bit.")
}

private fun withClientId(url: String, clientId: String): String {
    if ("client_id=" in url) return url
    val sep = if ('?' in url) '&' else '?'
    return "$url${sep}client_id=$clientId"
}

/** Connects to a signed-in SoundCloud account using the web player's OAuth cookie/header. */
object SoundCloudConnector : AccountConnector {
    override suspend fun connect(credentials: CapturedCredentials): AccountLibrary? {
        for (token in credentials.untriedBearers("soundcloud.com")) {
            val headers = mapOf(
                "Authorization" to "OAuth $token",
                "Accept" to "application/json",
                "User-Agent" to DESKTOP_USER_AGENT,
            )
            var cid = runCatching { resolveScClientId(forceRefresh = false) }.getOrNull() ?: return null
            var response = AccountHttp.get("$API/me?client_id=$cid", headers, SERVICE)
            if (response.status == 401 || response.status == 403) {
                val refreshed = runCatching { resolveScClientId(forceRefresh = true) }.getOrNull()
                if (refreshed != null && refreshed != cid) {
                    cid = refreshed
                    response = AccountHttp.get("$API/me?client_id=$cid", headers, SERVICE)
                }
            }
            when (response.status) {
                200 -> {
                    val me = AccountHttp.parse(response.body)
                    val userId = me?.idString("id")
                    if (userId != null) {
                        return SoundCloudLibrary(
                            headers = headers,
                            userId = userId,
                            accountName = me.string("username") ?: me.string("full_name"),
                            accountPhotoUrl = me.string("avatar_url")?.replace("-large.", "-t300x300."),
                            likedCount = me.long("likes_count")?.toInt(),
                        )
                    }
                    credentials.markTried(token)
                }
                429 -> throw AccountRetryLater(response.retryAfterSeconds?.coerceIn(2, 30) ?: 5)
                else -> credentials.markTried(token)
            }
        }
        return null
    }
}

private class SoundCloudLibrary(
    private val headers: Map<String, String>,
    private val userId: String,
    override val accountName: String?,
    override val accountPhotoUrl: String?,
    private val likedCount: Int?,
) : AccountLibrary {

    override suspend fun playlists(): List<RemotePlaylist> {
        val cid = resolveScClientId()
        val result = mutableListOf(
            RemotePlaylist(
                id = LIKED_ID,
                name = "Liked Tracks",
                trackCount = likedCount,
                subtitle = "Your liked tracks on SoundCloud",
                isLiked = true,
            ),
        )
        val seen = HashSet<String>()

        suspend fun collectFrom(startUrl: String) {
            var next: String? = withClientId(startUrl, cid)
            var pages = 0
            while (next != null && pages++ < MAX_PAGES) {
                val page = AccountHttp.getJson(withClientId(next, cid), headers, SERVICE, notFoundAsNull = true) ?: break
                page["collection"].array().forEach { el ->
                    val entry = el.obj() ?: return@forEach
                    val pl = entry["playlist"].obj() ?: entry
                    val id = pl.idString("id") ?: return@forEach
                    val title = pl.string("title") ?: return@forEach
                    if (!seen.add(id)) return@forEach
                    val isPrivate = pl.string("sharing").equals("private", ignoreCase = true)
                    val owner = pl["user"].obj()?.string("username")
                    val subtitle = when {
                        isPrivate -> "Private playlist"
                        !owner.isNullOrBlank() && !owner.equals(accountName, ignoreCase = true) -> owner
                        else -> null
                    }
                    val cover = (pl.string("artwork_url")
                        ?: pl["tracks"].array().firstNotNullOfOrNull { it.obj()?.string("artwork_url") })
                        ?.replace("-large.", "-t500x500.")
                    result += RemotePlaylist(
                        id = id,
                        name = title,
                        trackCount = pl.long("track_count")?.toInt(),
                        subtitle = subtitle,
                        coverUrl = cover,
                    )
                }
                next = page.string("next_href")
            }
        }

        collectFrom("$API/users/$userId/playlists/liked_and_owned?limit=50&linked_partitioning=1")
        collectFrom("$API/users/$userId/playlists_without_albums?limit=50&linked_partitioning=1")
        return result
    }

    override suspend fun tracks(playlist: RemotePlaylist): List<ImportedTrack> {
        val cid = resolveScClientId()
        if (playlist.isLiked) {
            val out = mutableListOf<ImportedTrack>()
            var next: String? = withClientId("$API/users/$userId/track_likes?limit=100&linked_partitioning=1", cid)
            var pages = 0
            while (next != null && pages++ < MAX_PAGES) {
                val page = AccountHttp.getJson(withClientId(next, cid), headers, SERVICE, notFoundAsNull = true) ?: break
                page["collection"].array().forEach { el ->
                    val entry = el.obj() ?: return@forEach
                    val track = entry["track"].obj() ?: return@forEach
                    track.toImportedTrack(addedAtMs = dateMs(entry.string("created_at")))?.let(out::add)
                }
                next = page.string("next_href")
            }
            return out
        }

        val pl = AccountHttp.getJson(
            "$API/playlists/${playlist.id}?representation=full&client_id=$cid",
            headers,
            SERVICE,
            notFoundAsNull = true,
        ) ?: return emptyList()
        val entries = pl["tracks"].array()
        return resolveTrackEntries(entries, cid)
    }

    private suspend fun resolveTrackEntries(entries: List<JsonElement>, cid: String): List<ImportedTrack> {
        val full = HashMap<String, JsonObject>()
        val missing = mutableListOf<String>()
        entries.forEach { el ->
            val obj = el.obj() ?: return@forEach
            val id = obj.idString("id") ?: return@forEach
            if (obj.string("title") != null) full[id] = obj else missing += id
        }
        missing.chunked(TRACK_BATCH).forEach { ids ->
            val resp = AccountHttp.get("$API/tracks?ids=${ids.joinToString(",")}&client_id=$cid", headers, SERVICE)
            if (resp.status in 200..299) {
                val arr = runCatching { AccountHttp.json.parseToJsonElement(resp.body) as? JsonArray }.getOrNull().orEmpty()
                arr.forEach { el ->
                    val obj = el.obj() ?: return@forEach
                    obj.idString("id")?.let { full[it] = obj }
                }
            }
        }
        return entries.mapNotNull { el ->
            el.obj()?.idString("id")?.let(full::get)?.toImportedTrack(addedAtMs = null)
        }
    }

    private fun JsonObject.toImportedTrack(addedAtMs: Long?): ImportedTrack? {
        val title = string("title") ?: return null
        val pub = this["publisher_metadata"].obj()
        val artist = pub?.string("artist") ?: this["user"].obj()?.string("username").orEmpty()
        val album = pub?.string("album_title")
        val durationMs = long("full_duration") ?: long("duration")
        val cover = string("artwork_url")?.replace("-large.", "-t500x500.")
        val id = idString("id")
        return ImportedTrack(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            thumbnailUrl = cover,
            addedAtMs = addedAtMs,
            sourceId = id?.let { "soundcloud:track:$it" },
        )
    }
}
