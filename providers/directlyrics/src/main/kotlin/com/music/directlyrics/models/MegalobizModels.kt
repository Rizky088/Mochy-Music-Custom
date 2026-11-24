package com.music.directlyrics.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MegalobizSearchResponse(
    val results: List<Hit> = emptyList(),
) {
    @Serializable
    data class Hit(
        val id: Long = 0L,
        val title: String = "",
        val artist: String = "",
        val duration: Double? = null,
        @SerialName("duration_diff") val durationDiff: Double? = null,
        val sync: String = "",
        val score: Double = 0.0,
        @SerialName("lrc_url") val lrcUrl: String = "",
    )
}
