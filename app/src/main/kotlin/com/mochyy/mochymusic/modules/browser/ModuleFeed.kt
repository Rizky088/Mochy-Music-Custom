// the module browser's side of mochy's module feed: signing in with discord through it, the list of modules from mochy's bar, and their zips

package com.mochyy.mochymusic.modules.browser

import java.util.concurrent.ConcurrentHashMap

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mochyy.mochymusic.BuildConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import com.mochyy.mochymusic.constants.SimulateForkKey
import com.mochyy.mochymusic.ui.component.DiscordMarkdown
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** one module in mochy's bar; [id] is its forum post, which is how its zip is asked for */
data class FeedModule(
    val id: String,
    val name: String,
    val moduleId: String?,
    val version: String?,
    val author: String?,
    /** the maker's words with discord's formatting marks still in, for the store page to style */
    val description: String,
    /** the same words with the marks taken out, for a card's two lines and the search */
    val blurb: String,
    val kind: FeedKind,
    /** the post's tags in the forum, which are the store's categories */
    val tags: List<String>,
    val paid: Boolean,
    /** paid, and this person can't see its forum in discord yet */
    val locked: Boolean,
    val icon: String?,
    /** the store page's wide picture, set with /publish or /banner in discord */
    val banner: String?,
    /** the store page's screenshots, set with /preview */
    val previews: List<String>,
    val install: FeedInstall,
    /** a pack's modules, each added on its own; empty for anything else */
    val pack: List<PackItem>,
    /** the zip's size in bytes (a pack's zips together), null for a linked module */
    val size: Long?,
    /** when it last changed, in milliseconds */
    val updated: Long?,
    /** where a linked module is added from, or the page a web one opens */
    val link: String?,
    val postUrl: String,
    val downloads: Int = 0,
)

/** how the store gets a module: its post's zip, a link added like a pasted one, a web page it opens, or a pack of several */
enum class FeedInstall { Zip, Link, Web, Pack }

/** one module in a pack: a zip in the pack's post, or a link it's added from like a pasted one */
data class PackItem(
    val id: String,
    val isLink: Boolean,
    val name: String,
    val moduleId: String?,
    val version: String?,
    val size: Long?,
    val icon: String?,
    val link: String?,
)

enum class FeedKind { Look, Player, Source, Other }

/** who's signed in, for the chip at the top; [member] is whether they're in mochy's bar */
data class FeedPerson(val name: String, val avatar: String?, val member: Boolean, val invite: String?)

sealed interface FeedResult {
    /** [chart] is best from community in order, [categories] the forum's tags the chips offer */
    data class Modules(val person: FeedPerson, val modules: List<FeedModule>, val chart: List<FeedModule>, val categories: List<String>) : FeedResult
    data class NotMember(val person: FeedPerson?, val invite: String?) : FeedResult
    data object SignedOut : FeedResult
    /** the feed hasn't read the forums yet, a minute or so after it's first set up */
    data object WarmingUp : FeedResult
    data object Offline : FeedResult
    /** a fork or changed copy: the feed only answers official builds */
    data object Unofficial : FeedResult
    /** this build has no feed address yet */
    data object NotSetUp : FeedResult
}

@Singleton
class ModuleFeed @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val client by lazy {
        OkHttpClient.Builder()
            .dispatcher(com.mochyy.mochymusic.utils.AppProxy.dispatcher)
            .connectionPool(com.mochyy.mochymusic.utils.AppProxy.connectionPool)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val countsClient by lazy {
        client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private val zipUrls = ConcurrentHashMap<String, String>()
    private val _session = MutableStateFlow<String?>("custom_store_public")

    /** public custom store doesn't require Discord sign-in; always active */
    val session: StateFlow<String?> = _session.asStateFlow()

    val isSetUp: Boolean get() = FeedUrl.isNotBlank()

    private fun isAllowed(): Boolean = !context.dataStore.get(SimulateForkKey, false)

    fun signInUri(): Uri? = null

    fun finishSignIn(redirect: Uri): Boolean = false

    suspend fun signOut() = withContext(Dispatchers.IO) {
        Unit
    }

    suspend fun load(): FeedResult = withContext(Dispatchers.IO) {
        if (!isSetUp) return@withContext FeedResult.NotSetUp
        if (!isAllowed()) return@withContext FeedResult.Unofficial
        coroutineScope {
            val countsDeferred = async { fetchCounts() }
            val response = try {
                val url = if (FeedUrl.endsWith(".json")) FeedUrl else "$FeedUrl/modules.json"
                client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Cache-Control", "no-cache")
                        .build()
                ).execute()
            } catch (e: IOException) {
                countsDeferred.cancel()
                return@coroutineScope FeedResult.Offline
            }
            response.use {
                val body = runCatching { JSONObject(it.body.string()) }.getOrNull() ?: JSONObject()
                when (it.code) {
                    200 -> modulesPage(body, countsDeferred.await())
                    503 -> {
                        countsDeferred.cancel()
                        FeedResult.WarmingUp
                    }
                    else -> {
                        countsDeferred.cancel()
                        FeedResult.Offline
                    }
                }
            }
        }
    }

    private fun fetchCounts(): Map<String, Int> {
        val base = BuildConfig.STORE_COUNTS_URL.trim().trimEnd('/')
        if (base.isEmpty()) return emptyMap()
        return runCatching {
            countsClient.newCall(
                Request.Builder()
                    .url("$base/counts")
                    .header("Cache-Control", "no-cache")
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) return@use emptyMap()
                val obj = JSONObject(res.body.string())
                buildMap {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optInt(k, 0)
                        if (v >= 0) put(k, v)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun recordDownload(postId: String): Int? = withContext(Dispatchers.IO) {
        val base = BuildConfig.STORE_COUNTS_URL.trim().trimEnd('/')
        if (base.isEmpty() || !PostIdPattern.matches(postId)) return@withContext null
        runCatching {
            countsClient.newCall(
                Request.Builder()
                    .url("$base/hit/$postId")
                    .post("".toRequestBody(null))
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) return@use null
                JSONObject(res.body.string()).optInt("count", -1).takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    /** downloads the module's zip (or pack item zip) from its catalog zipUrl or store/zips/ folder */
    suspend fun download(postId: String, item: String? = null): File? = withContext(Dispatchers.IO) {
        if (!isSetUp || !isAllowed()) return@withContext null
        if (!PostIdPattern.matches(postId) || (item != null && !ItemIdPattern.matches(item))) return@withContext null
        val folder = File(context.cacheDir, "module_browser").apply { mkdirs() }
        val file = File(folder, if (item != null) "$postId-$item.zip" else "$postId.zip")
        val key = if (item != null) "$postId:$item" else postId
        val base = FeedUrl.removeSuffix("/modules.json").trimEnd('/')
        val url = zipUrls[key]
            ?: if (item != null) "$base/zips/$postId-$item.zip" else "$base/zips/$postId.zip"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@use null
                file.outputStream().use { out -> res.body.byteStream().copyTo(out) }
                file
            }
        }.getOrNull()
    }

    // every call carries the build's native signature over what it asks for, and never goes out unsigned
    private fun call(request: Request.Builder, session: String): Response? {
        if (!isAllowed()) return null
        val built = request.header("Authorization", "Bearer $session").build()
        val (time, signature) = StoreSignature.sign(context, built.method, built.url.encodedPath, session) ?: return null
        val signed = built.newBuilder().header("X-Mochy-Time", time).header("X-Mochy-Sign", signature).build()
        return client.newCall(signed).execute()
    }

    private fun forgetSession() {
        prefs.edit { remove(SessionKey) }
        _session.value = null
    }

    private fun personOf(json: JSONObject?): FeedPerson? = json?.optString("name")?.takeIf { it.isNotBlank() }?.let { name ->
        FeedPerson(name, json.optString("avatar").takeIf { it.startsWith("https://") }, json.optBoolean("member"), json.optString("invite").takeIf { it.startsWith("https://") })
    }

    private fun modulesPage(body: JSONObject, counts: Map<String, Int> = emptyMap()): FeedResult.Modules {
        val modules = modulesOf(body, counts)
        val byId = modules.associateBy { it.id }
        val chart = body.optJSONArray("chart")?.let { ids -> (0 until ids.length()).mapNotNull { byId[ids.optString(it)] } }.orEmpty()
        return FeedResult.Modules(
            person = personOf(body.optJSONObject("me")) ?: FeedPerson("", null, member = true, invite = null),
            modules = modules,
            chart = chart,
            categories = body.optJSONArray("categories")?.let { names -> (0 until names.length()).map { names.optString(it) } }.orEmpty().filter { it.isNotBlank() },
        )
    }

    private fun packItemOf(parentId: String, item: JSONObject?): PackItem? {
        if (item == null || item.optBoolean("hidden", false)) return null
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        item.optString("zipUrl").takeIf { it.startsWith("https://") }?.let { zipUrls["$parentId:$id"] = it }
        return PackItem(
            id = id,
            isLink = item.optString("kind") == "link",
            name = item.optString("name").ifBlank { id },
            moduleId = item.optString("moduleId").takeIf { it.isNotBlank() && !item.isNull("moduleId") },
            version = item.optString("version").takeIf { it.isNotBlank() && !item.isNull("version") },
            size = item.optLong("size", -1).takeIf { it >= 0 && !item.isNull("size") },
            icon = item.optString("icon").takeIf { it.startsWith("https://") },
            link = item.optString("link").takeIf { it.startsWith("https://") },
        )
    }

    private fun modulesOf(body: JSONObject, counts: Map<String, Int> = emptyMap()): List<FeedModule> {
        val list = body.optJSONArray("modules") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            if (item.optBoolean("hidden", false)) return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            item.optString("zipUrl").takeIf { it.startsWith("https://") }?.let { zipUrls[id] = it }
            FeedModule(
                id = id,
                name = item.optString("name").ifBlank { id },
                moduleId = item.optString("moduleId").takeIf { it.isNotBlank() && !item.isNull("moduleId") },
                version = item.optString("version").takeIf { it.isNotBlank() && !item.isNull("version") },
                author = item.optString("author").takeIf { it.isNotBlank() && !item.isNull("author") },
                description = item.optString("description"),
                // worked out here on the feed's own thread, so a list of cards never parses while it scrolls
                blurb = DiscordMarkdown.plain(item.optString("description")),
                kind = when (item.optString("kind")) {
                    "look" -> FeedKind.Look
                    "player" -> FeedKind.Player
                    "source" -> FeedKind.Source
                    else -> FeedKind.Other
                },
                tags = item.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it) } }.orEmpty(),
                paid = item.optBoolean("paid"),
                locked = item.optBoolean("locked"),
                icon = item.optString("icon").takeIf { it.startsWith("https://") },
                banner = item.optString("banner").takeIf { it.startsWith("https://") },
                previews = item.optJSONArray("previews")?.let { list ->
                    (0 until list.length()).map { list.optString(it) }.filter { it.startsWith("https://") }
                }.orEmpty(),
                install = when (item.optString("install")) {
                    "link" -> FeedInstall.Link
                    "web" -> FeedInstall.Web
                    "pack" -> FeedInstall.Pack
                    else -> FeedInstall.Zip
                },
                pack = item.optJSONArray("pack")?.let { items -> (0 until items.length()).mapNotNull { packItemOf(id, items.optJSONObject(it)) } }.orEmpty(),
                link = item.optString("link").takeIf { it.startsWith("https://") },
                size = item.optLong("size", -1).takeIf { it >= 0 && !item.isNull("size") },
                updated = item.optLong("updated", -1).takeIf { it > 0 },
                postUrl = item.optString("discordPostUrl").takeIf { it.startsWith("https://") }
                    ?: item.optString("postUrl").takeIf { it.startsWith("https://") }
                    .orEmpty(),
                downloads = counts[id] ?: item.optInt("downloads", 0).coerceAtLeast(0),
            )
        }
    }

    companion object {
        // the worker's address (see the mochy-module-feed project); blank makes the browser say it isn't set up
        const val FeedUrl = "https://raw.githubusercontent.com/Rizky088/Mochy-Music-Custom/main/store"

        private const val SessionKey = "session"
        private const val PrefsName = "module_browser"
        private const val StateKey = "state"
        private val SessionPattern = Regex("^[0-9a-f]{64}$")
        private val PostIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val ItemIdPattern = Regex("^[A-Za-z0-9_-]{1,128}$")
    }
}
