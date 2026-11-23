package com.mochyy.mochymusic.modules.soundcloud

import com.mochyy.mochymusic.module.AccountState
import com.mochyy.mochymusic.module.CatalogTrack
import com.mochyy.mochymusic.module.DiagnosticStep
import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.MatchedTrack
import com.mochyy.mochymusic.module.ModuleChoice
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleInfo
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.SignInSpec
import com.mochyy.mochymusic.module.SourceModule
import com.mochyy.mochymusic.module.SourceStream
import com.mochyy.mochymusic.module.StreamFormat
import com.mochyy.mochymusic.module.StreamOption
import com.mochyy.mochymusic.module.TrackMatcher
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * SoundCloud audio source:
 * - Free public tracks work with zero login using SoundCloud's public web client_id.
 * - Optional in-app WebView sign-in lets users connect their own SoundCloud Go+ subscription
 *   to unlock full streams for Go+ tracks.
 * - Paywalled Go+ tracks played without Go+ are flagged as "Go+ Preview (30s)" so the player
 *   shows the notice beside the artist name.
 */
class SoundCloudModule : SourceModule {

    override val info = ModuleInfo(
        id = Id,
        name = "SoundCloud",
        version = "1.0",
        description = "Streams public SoundCloud tracks for free without login. Optionally sign in to unlock your SoundCloud Go+ subscription.",
    )

    override val signIn = SignInSpec(
        startUrl = "https://soundcloud.com/signin",
        origins = listOf(
            "https://soundcloud.com",
            "https://m.soundcloud.com",
            "https://secure.soundcloud.com",
            "https://accounts.google.com",
            "https://appleid.apple.com",
        ),
        sessionOrigins = setOf(
            "https://soundcloud.com",
            "https://m.soundcloud.com",
            "https://api-v2.soundcloud.com",
            "https://secure.soundcloud.com",
        ),
        desktopSite = false,
        title = "Sign in to SoundCloud",
        subtitle = "Optional: unlocks full SoundCloud Go+ tracks",
    )

    override val choices = listOf(
        ModuleChoice(
            id = ChoiceQuality,
            label = "Stream",
            options = listOf(
                StreamOption(OptionBest, "Best available", "Uses AAC/Opus HLS or MP3 at the highest available bitrate"),
                StreamOption(OptionProgressive, "MP3 Progressive", "Single-file MP3 stream (ideal for downloads when available)"),
            ),
        ),
    )

    private lateinit var host: ModuleHost
    private val json = Json { ignoreUnknownKeys = true }
    private val matches = ConcurrentHashMap<String, ScTrack>()
    private val misses = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cachedClientId: String? = null

    override fun attach(host: ModuleHost) {
        this.host = host
    }

    private var oauthToken: String?
        get() = host.storage.get(KeyOAuthToken)?.takeIf { it.isNotBlank() }
        set(value) = host.storage.set(KeyOAuthToken, value)

    val hasAccountLogin: Boolean
        get() = ::host.isInitialized && oauthToken != null

    override fun accountState(): AccountState {
        if (!::host.isInitialized) return AccountState.SignedIn(FreeLabel)
        val token = oauthToken
        if (token != null) {
            val label = host.storage.get(KeyAccountLabel)?.takeIf { it.isNotBlank() } ?: "Signed in"
            return AccountState.SignedIn(label)
        }
        val reason = host.storage.get("unreachable") ?: return AccountState.SignedIn(FreeLabel)
        val markedAt = host.storage.get("unreachable_at")?.toLongOrNull() ?: 0L
        return if (System.currentTimeMillis() - markedAt < UnreachableRetryMs) {
            AccountState.Expired(reason)
        } else {
            AccountState.SignedIn(FreeLabel)
        }
    }

    override fun onSignInHeader(name: String, value: String, url: String): Boolean {
        if (!name.equals("authorization", ignoreCase = true)) return false
        val trimmed = value.trim()
        val token = when {
            trimmed.startsWith("OAuth ", ignoreCase = true) -> trimmed.substring(6).trim()
            trimmed.startsWith("Bearer ", ignoreCase = true) -> trimmed.substring(7).trim()
            else -> return false
        }
        if (token.length < 10) return false
        oauthToken = token
        matches.clear()
        misses.clear()
        return true
    }

    override suspend fun verifyAccount(): AccountState {
        val cid = ensureClientId(forceRefresh = false)
        if (cid == null) {
            val reason = "Couldn't reach SoundCloud"
            markUnreachable(reason)
            return AccountState.Expired(reason)
        }
        val token = oauthToken
        if (token != null) {
            val me = runCatching {
                host.http(HttpRequest("$ApiBase/me?client_id=$cid", headers = apiHeaders(token)))
            }.getOrNull()
            if (me?.ok == true) {
                val obj = runCatching { json.parseToJsonElement(me.text) as? JsonObject }.getOrNull()
                val username = obj?.str("username") ?: obj?.str("full_name") ?: "SoundCloud"
                val sub = me.text.contains("high_tier", ignoreCase = true) ||
                    me.text.contains("consumer-subs", ignoreCase = true) ||
                    me.text.contains("go+", ignoreCase = true)
                val label = if (sub) "$username · Go+" else username
                host.storage.set(KeyAccountLabel, label)
                host.storage.set("unreachable", null)
                return AccountState.SignedIn(label)
            }
            if (me?.code == 401 || me?.code == 403) {
                oauthToken = null
                host.storage.set(KeyAccountLabel, null)
                return AccountState.SignedIn(FreeLabel)
            }
        }
        val probe = runCatching {
            host.http(HttpRequest("$ApiBase/search/tracks?q=test&limit=1&client_id=$cid", headers = apiHeaders(null)))
        }.getOrNull()
        return if (probe?.ok == true) {
            host.storage.set("unreachable", null)
            AccountState.SignedIn("$FreeLabel · reachable")
        } else {
            val reason = "SoundCloud didn't answer (${probe?.code ?: "offline"})"
            markUnreachable(reason)
            AccountState.Expired(reason)
        }
    }

    override fun signOut() {
        if (::host.isInitialized) {
            oauthToken = null
            host.storage.set(KeyAccountLabel, null)
            host.storage.set("unreachable", null)
        }
        matches.clear()
        misses.clear()
    }

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult =
        diagnose(track, picks) {}

    override suspend fun diagnose(
        track: TrackQuery,
        picks: Map<String, String>,
        report: (DiagnosticStep) -> Unit,
    ): ResolveResult {
        val token = oauthToken
        val key = "${if (token != null) "auth" else "free"}|${track.title}|${track.artists.joinToString(",")}"
        val missedAt = misses[key]
        if (missedAt != null && System.currentTimeMillis() - missedAt < MissMemoryMs) {
            report(DiagnosticStep("Find the song", false, "Not on SoundCloud"))
            return ResolveResult.NotFound("Not on SoundCloud")
        }

        report(DiagnosticStep("Find the song", null, "Searching SoundCloud…"))
        val cid = ensureClientId(forceRefresh = false) ?: run {
            report(DiagnosticStep("Find the song", false, "Couldn't get SoundCloud client ID"))
            return ResolveResult.Unavailable("Couldn't reach SoundCloud")
        }

        val found = matches[key] ?: run {
            val query = "${TrackMatcher.core(track.title)} ${track.artists.firstOrNull().orEmpty()}".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            var resp = runCatching {
                host.http(HttpRequest("$ApiBase/search/tracks?q=$encoded&limit=12&client_id=$cid", headers = apiHeaders(token)))
            }.getOrNull()
            if (resp?.code == 401 || resp?.code == 403) {
                val freshCid = ensureClientId(forceRefresh = true)
                if (freshCid != null) {
                    resp = runCatching {
                        host.http(HttpRequest("$ApiBase/search/tracks?q=$encoded&limit=12&client_id=$freshCid", headers = apiHeaders(token)))
                    }.getOrNull()
                }
            }
            if (resp == null || !resp.ok) {
                val msg = "SoundCloud answered ${resp?.code ?: "offline"}"
                report(DiagnosticStep("Find the song", false, msg))
                return ResolveResult.Unavailable(msg)
            }
            host.storage.set("unreachable", null)
            val candidates = parseTracks(resp.text)
            val best = TrackMatcher.ranked(track, candidates.map { it.catalog }).firstOrNull()
                ?.let { scored -> candidates.first { it.catalog === scored.track } }
            if (best == null) {
                misses[key] = System.currentTimeMillis()
                report(DiagnosticStep("Find the song", false, "No matching recording of “${track.title}”"))
                return ResolveResult.NotFound("No matching recording on SoundCloud")
            }
            matches[key] = best
            best
        }

        report(DiagnosticStep("Find the song", true, "${found.catalog.title} · ${found.catalog.artist}"))
        report(DiagnosticStep("Stream", null, "Opening stream…"))

        val preferProgressive = picks[ChoiceQuality] == OptionProgressive
        val chosen = pickTranscoding(found.transcodings, preferProgressive)
        if (chosen == null) {
            report(DiagnosticStep("Stream", false, "No playable stream on SoundCloud"))
            return ResolveResult.NotFound("No playable stream on SoundCloud")
        }

        val activeCid = cachedClientId ?: ensureClientId(forceRefresh = false)
            ?: return ResolveResult.Unavailable("Missing SoundCloud client ID")
        val sep = if ('?' in chosen.url) '&' else '?'
        val resolveUrl = buildString {
            append(chosen.url)
            append(sep).append("client_id=").append(activeCid)
            if (!found.trackAuthorization.isNullOrBlank()) {
                append("&track_authorization=").append(URLEncoder.encode(found.trackAuthorization, "UTF-8"))
            }
        }
        val mediaResp = runCatching {
            host.http(HttpRequest(resolveUrl, headers = apiHeaders(token)))
        }.getOrNull()
        val streamUrl = mediaResp?.takeIf { it.ok }?.let { resp ->
            runCatching { (json.parseToJsonElement(resp.text) as? JsonObject)?.str("url") }.getOrNull()
        }
        if (streamUrl.isNullOrBlank()) {
            report(DiagnosticStep("Stream", false, "SoundCloud refused stream (${mediaResp?.code ?: "offline"})"))
            return ResolveResult.Unavailable("SoundCloud stream couldn't be opened")
        }

        val isSnip = chosen.snipped || found.policy.equals("SNIP", ignoreCase = true)
        val format = if (chosen.protocol == "progressive") StreamFormat.Progressive else StreamFormat.Hls
        val codec = when {
            chosen.mimeType.contains("mp4", ignoreCase = true) || chosen.mimeType.contains("aac", ignoreCase = true) -> "AAC"
            chosen.mimeType.contains("ogg", ignoreCase = true) || chosen.mimeType.contains("opus", ignoreCase = true) -> "OPUS"
            else -> "MP3"
        }
        val qualityLabel = if (isSnip) "$codec · $GoPlusPreviewTag" else codec
        report(DiagnosticStep("Stream", true, qualityLabel))

        return ResolveResult.Found(
            SourceStream(
                url = streamUrl,
                format = format,
                headers = mapOf("User-Agent" to WebUserAgent),
                sourceLabel = "SoundCloud",
                qualityLabel = qualityLabel,
                codec = codec,
                sampleRateHz = 44_100,
                matched = MatchedTrack(
                    id = found.catalog.id,
                    title = found.catalog.title,
                    artist = found.catalog.artist,
                    album = found.catalog.album,
                    durationMs = found.catalog.durationMs,
                    confidence = 0.85f,
                ),
            ),
        )
    }

    private fun pickTranscoding(list: List<ScTranscoding>, preferProgressive: Boolean): ScTranscoding? {
        val playable = list.filter { it.protocol == "progressive" || it.protocol == "hls" }
        if (playable.isEmpty()) return null
        val fullOnly = playable.filterNot { it.snipped }.ifEmpty { playable }
        return if (preferProgressive) {
            fullOnly.firstOrNull { it.protocol == "progressive" }
                ?: fullOnly.firstOrNull { it.protocol == "hls" && it.mimeType.contains("mp4", ignoreCase = true) }
                ?: fullOnly.firstOrNull { it.protocol == "hls" && it.mimeType.contains("mpeg", ignoreCase = true) }
                ?: fullOnly.first()
        } else {
            fullOnly.firstOrNull { it.protocol == "hls" && it.mimeType.contains("mp4", ignoreCase = true) }
                ?: fullOnly.firstOrNull { it.protocol == "progressive" }
                ?: fullOnly.firstOrNull { it.protocol == "hls" && it.mimeType.contains("mpeg", ignoreCase = true) }
                ?: fullOnly.first()
        }
    }

    private fun parseTracks(body: String): List<ScTrack> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val collection = root["collection"] as? JsonArray ?: return emptyList()
        return collection.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if (obj.str("policy").equals("BLOCK", ignoreCase = true)) return@mapNotNull null
            val id = obj.str("id") ?: (obj["id"] as? JsonPrimitive)?.longOrNull?.toString() ?: return@mapNotNull null
            val title = obj.str("title") ?: return@mapNotNull null
            val pubArtist = (obj["publisher_metadata"] as? JsonObject)?.str("artist")
            val uploader = (obj["user"] as? JsonObject)?.str("username")
            val artist = pubArtist ?: uploader ?: ""
            val durationMs = (obj["full_duration"] as? JsonPrimitive)?.longOrNull
                ?: (obj["duration"] as? JsonPrimitive)?.longOrNull
            val transcodings = ((obj["media"] as? JsonObject)?.get("transcodings") as? JsonArray).orEmpty()
                .mapNotNull { tEl ->
                    val t = tEl as? JsonObject ?: return@mapNotNull null
                    val url = t.str("url") ?: return@mapNotNull null
                    val fmt = t["format"] as? JsonObject
                    val protocol = fmt?.str("protocol").orEmpty()
                    val mime = fmt?.str("mime_type").orEmpty()
                    val snipped = (t["snipped"] as? JsonPrimitive)?.booleanOrNull == true
                    ScTranscoding(url = url, protocol = protocol, mimeType = mime, snipped = snipped)
                }
            if (transcodings.isEmpty()) return@mapNotNull null
            ScTrack(
                catalog = CatalogTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = (obj["publisher_metadata"] as? JsonObject)?.str("album_title"),
                    durationMs = durationMs,
                ),
                policy = obj.str("policy").orEmpty(),
                trackAuthorization = obj.str("track_authorization"),
                transcodings = transcodings,
            )
        }
    }

    private fun ensureClientId(forceRefresh: Boolean): String? {
        if (!forceRefresh) cachedClientId?.let { return it }
        if (!forceRefresh) {
            host.storage.get(KeyClientId)?.takeIf { it.isNotBlank() }?.let {
                cachedClientId = it
                return it
            }
        }
        val home = runCatching {
            host.http(HttpRequest("https://soundcloud.com/", headers = mapOf("User-Agent" to WebUserAgent)))
        }.getOrNull()?.takeIf { it.ok }?.text ?: return cachedClientId

        HtmlClientIdRegex.find(home)?.groupValues?.getOrNull(1)?.let { found ->
            saveClientId(found)
            return found
        }
        // Fallback: scan SoundCloud web player JS bundles (same approach as yt-dlp)
        val scripts = ScriptSrcRegex.findAll(home).map { it.groupValues[1] }.toList().asReversed()
        for (src in scripts.take(8)) {
            val js = runCatching {
                host.http(HttpRequest(src, headers = mapOf("User-Agent" to WebUserAgent)))
            }.getOrNull()?.takeIf { it.ok }?.text ?: continue
            JsClientIdRegex.find(js)?.groupValues?.getOrNull(1)?.let { found ->
                saveClientId(found)
                return found
            }
        }
        return cachedClientId
    }

    private fun saveClientId(id: String) {
        cachedClientId = id
        if (::host.isInitialized) host.storage.set(KeyClientId, id)
    }

    private fun markUnreachable(reason: String) {
        if (!::host.isInitialized) return
        host.storage.set("unreachable", reason)
        host.storage.set("unreachable_at", System.currentTimeMillis().toString())
    }

    private fun apiHeaders(token: String?): Map<String, String> = buildMap {
        put("User-Agent", WebUserAgent)
        put("Accept", "application/json")
        if (!token.isNullOrBlank()) put("Authorization", "OAuth $token")
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private class ScTranscoding(val url: String, val protocol: String, val mimeType: String, val snipped: Boolean)
    private class ScTrack(
        val catalog: CatalogTrack,
        val policy: String,
        val trackAuthorization: String?,
        val transcodings: List<ScTranscoding>,
    )

    companion object {
        const val Id = "soundcloud"
        const val GoPlusPreviewTag = "Go+ Preview (30s)"
        private const val ApiBase = "https://api-v2.soundcloud.com"
        private const val FreeLabel = "Free · No account needed"
        private const val ChoiceQuality = "quality"
        private const val OptionBest = "best"
        private const val OptionProgressive = "progressive"
        private const val KeyOAuthToken = "oauth_token"
        private const val KeyAccountLabel = "account_label"
        private const val KeyClientId = "client_id"
        private const val UnreachableRetryMs = 5 * 60 * 1000L
        private const val MissMemoryMs = 10 * 60 * 1000L
        private const val WebUserAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        private val HtmlClientIdRegex = Regex(""""clientId"\s*:\s*"([A-Za-z0-9]{16,})"""")
        private val JsClientIdRegex = Regex("""client_id\s*[:=]\s*["']([A-Za-z0-9]{20,40})["']""")
        private val ScriptSrcRegex = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
    }
}
