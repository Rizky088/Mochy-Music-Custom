// NotRecommended.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HiddenSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val hiddenAt: Long = 0L,
)

/** songs you asked us to block / stop suggesting; autoplay, radios, playback and home's picks all skip them */
object NotRecommended {
    const val PREFS_NAME = "not_recommended"
    private const val KEY_SONGS = "songs"
    private const val KEY_META = "songs_meta"

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var cached: Set<String>? = null

    private val _hiddenSongs = MutableStateFlow<List<HiddenSong>>(emptyList())
    val hiddenSongs: StateFlow<List<HiddenSong>> = _hiddenSongs.asStateFlow()

    // opening the file here starts loading it in the background, so the first check later doesn't wait on disk
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    val songs: Set<String>
        get() = cached ?: synchronized(this) {
            cached ?: loadFromPrefs()
        }

    operator fun contains(songId: String): Boolean = songId in songs

    @Synchronized
    fun add(songId: String) = add(songId, title = "", artist = "", thumbnailUrl = null)

    @Synchronized
    fun add(songId: String, title: String, artist: String, thumbnailUrl: String?) {
        if (songId.isBlank()) return
        val nextIds = songs + songId
        val existing = _hiddenSongs.value.associateBy { it.id }.toMutableMap()
        val prev = existing[songId]
        existing[songId] = HiddenSong(
            id = songId,
            title = title.ifBlank { prev?.title.orEmpty() },
            artist = artist.ifBlank { prev?.artist.orEmpty() },
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() } ?: prev?.thumbnailUrl,
            hiddenAt = System.currentTimeMillis(),
        )
        saveAll(nextIds, existing.values.filter { it.id in nextIds }.sortedByDescending { it.hiddenAt })
    }

    @Synchronized
    fun remove(songId: String) {
        val nextIds = songs - songId
        val nextList = _hiddenSongs.value.filterNot { it.id == songId }
        saveAll(nextIds, nextList)
    }

    @Synchronized
    fun clearAll() {
        saveAll(emptySet(), emptyList())
    }

    @Synchronized
    private fun loadFromPrefs(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        val ids = prefs.getStringSet(KEY_SONGS, null).orEmpty().toSet()
        val metaMap = mutableMapOf<String, HiddenSong>()
        runCatching {
            val raw = prefs.getString(KEY_META, null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                    metaMap[id] = HiddenSong(
                        id = id,
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        thumbnailUrl = o.optString("thumb").takeIf { it.isNotBlank() },
                        hiddenAt = o.optLong("at", 0L),
                    )
                }
            }
        }
        val list = ids.map { id ->
            metaMap[id] ?: HiddenSong(id = id, title = "", artist = "", thumbnailUrl = null, hiddenAt = 0L)
        }.sortedByDescending { it.hiddenAt }
        cached = ids
        _hiddenSongs.value = list
        return ids
    }

    private fun saveAll(nextIds: Set<String>, nextList: List<HiddenSong>) {
        cached = nextIds
        _hiddenSongs.value = nextList
        if (!::prefs.isInitialized) return
        val arr = JSONArray()
        nextList.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("thumb", item.thumbnailUrl.orEmpty())
                    .put("at", item.hiddenAt),
            )
        }
        prefs.edit {
            putStringSet(KEY_SONGS, nextIds)
            putString(KEY_META, arr.toString())
        }
    }
}
