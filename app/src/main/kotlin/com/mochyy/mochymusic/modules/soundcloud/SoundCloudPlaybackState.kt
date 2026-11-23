package com.mochyy.mochymusic.modules.soundcloud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tracks which mediaIds are currently playing as a SoundCloud Go+ 30-second preview. */
object SoundCloudPlaybackState {
    private val _snippedMediaIds = MutableStateFlow<Set<String>>(emptySet())
    val snippedMediaIds: StateFlow<Set<String>> = _snippedMediaIds.asStateFlow()

    fun update(mediaId: String, isGoPlusPreview: Boolean) {
        _snippedMediaIds.update { current ->
            if (isGoPlusPreview) current + mediaId else current - mediaId
        }
    }
}
