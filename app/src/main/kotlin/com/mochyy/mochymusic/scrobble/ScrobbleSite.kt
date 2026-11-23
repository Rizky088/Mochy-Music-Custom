// ScrobbleSite.kt

package com.mochyy.mochymusic.scrobble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.constants.EnableLastFMScrobblingKey
import com.mochyy.mochymusic.constants.EnableLibreFmScrobblingKey
import com.mochyy.mochymusic.constants.EnableListenBrainzScrobblingKey
import com.mochyy.mochymusic.constants.LastFMSessionKey
import com.mochyy.mochymusic.constants.LastFMUsernameKey
import com.mochyy.mochymusic.constants.LibreFmSessionKey
import com.mochyy.mochymusic.constants.LibreFmUsernameKey
import com.mochyy.mochymusic.constants.ListenBrainzTokenKey
import com.mochyy.mochymusic.constants.ListenBrainzUsernameKey
import com.music.lastfm.Audioscrobbler

/**
 * The services Mochy scrobbles to: Last.fm, Libre.fm, and ListenBrainz.
 * Last.fm also supports user-supplied Pro API keys in Mochy Ext when build-time keys are absent.
 */
enum class ScrobbleSite(
    val label: String,
    val sessionKey: Preferences.Key<String>,
    val usernameKey: Preferences.Key<String>,
    /** Scrobbling on or paused, with the account kept either way. */
    val enabledKey: Preferences.Key<Boolean>,
    private val profilePage: String,
    /** where someone without an account makes one */
    val signUpUrl: String,
    private val defaultApi: Audioscrobbler,
) {
    LASTFM(
        label = "Last.fm",
        sessionKey = LastFMSessionKey,
        usernameKey = LastFMUsernameKey,
        enabledKey = EnableLastFMScrobblingKey,
        profilePage = "https://www.last.fm/user/",
        signUpUrl = "https://www.last.fm/join",
        defaultApi = Audioscrobbler(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        ),
    ),
    LIBREFM(
        label = "Libre.fm",
        sessionKey = LibreFmSessionKey,
        usernameKey = LibreFmUsernameKey,
        enabledKey = EnableLibreFmScrobblingKey,
        profilePage = "https://libre.fm/user/",
        signUpUrl = "https://libre.fm/register.php",
        defaultApi = Audioscrobbler(
            apiRoot = "https://libre.fm/2.0/",
            apiKey = BuildConfig.LIBREFM_API_KEY,
            secret = BuildConfig.LIBREFM_SECRET,
            signsInWithToken = true,
        ),
    ),
    LISTENBRAINZ(
        label = "ListenBrainz",
        sessionKey = ListenBrainzTokenKey,
        usernameKey = ListenBrainzUsernameKey,
        enabledKey = EnableListenBrainzScrobblingKey,
        profilePage = "https://listenbrainz.org/user/",
        signUpUrl = "https://listenbrainz.org/login/",
        defaultApi = Audioscrobbler(
            apiRoot = ListenBrainzClient.DefaultServerUrl,
            apiKey = "listenbrainz",
            secret = "listenbrainz",
        ),
    );

    val api: Audioscrobbler
        get() = if (this == LASTFM && customLastFmKey.isNotBlank() && customLastFmSecret.isNotBlank()) {
            Audioscrobbler(
                apiRoot = "https://ws.audioscrobbler.com/2.0/",
                apiKey = customLastFmKey,
                secret = customLastFmSecret,
            )
        } else {
            defaultApi
        }

    fun profileUrl(username: String) = profilePage + username

    companion object {
        var customLastFmKey by mutableStateOf("")
            private set
        var customLastFmSecret by mutableStateOf("")
            private set

        val hasBuiltInLastFmKey: Boolean
            get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_SECRET.isNotBlank()

        fun updateLastFmCustomCredentials(apiKey: String, secret: String) {
            customLastFmKey = apiKey.trim()
            customLastFmSecret = secret.trim()
        }
    }
}
