// MegalobizLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.mochyy.mochymusic.constants.EnableMegalobizKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.directlyrics.Megalobiz

object MegalobizLyricsProvider : LyricsProvider {
    override val name = "Megalobiz"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMegalobizKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Megalobiz.getLyrics(title, artist, duration)
}
