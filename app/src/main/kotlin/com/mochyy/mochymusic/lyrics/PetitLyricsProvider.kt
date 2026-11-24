// PetitLyricsProvider.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import com.mochyy.mochymusic.constants.EnablePetitLyricsKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.directlyrics.PetitLyrics

object PetitLyricsProvider : LyricsProvider {
    override val name = "PetitLyrics"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnablePetitLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = PetitLyrics.getLyrics(title, artist, duration)
}
