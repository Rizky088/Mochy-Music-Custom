package com.mochyy.mochymusic.ui.screens.playlist

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.utils.YTPlayerUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PreviewDurationMs = 25_000L

@Stable
internal class AddMusicPreviewPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mainConnection: PlayerConnection?,
) {
    var activeId by mutableStateOf<String?>(null)
        private set
    var loadingId by mutableStateOf<String?>(null)
        private set

    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var stopJob: Job? = null
    private var pausedMainPlayer = false

    fun toggle(metadata: MediaMetadata) {
        if (activeId == metadata.id || loadingId == metadata.id) {
            stop(resumeMain = true)
            return
        }
        start(metadata)
    }

    @OptIn(UnstableApi::class)
    private fun start(metadata: MediaMetadata) {
        stop(resumeMain = false)
        loadingId = metadata.id
        activeId = null

        val main = mainConnection?.player
        if (main != null && main.isPlaying) {
            main.pause()
            pausedMainPlayer = true
        }

        loadJob = scope.launch {
            try {
                val localUri = metadata.localUri
                val resolvedUrl: String
                val headers: Map<String, String>
                if (!localUri.isNullOrBlank()) {
                    resolvedUrl = localUri
                    headers = emptyMap()
                } else {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val data = withContext(Dispatchers.IO) {
                        YTPlayerUtils.playerResponseForPlayback(
                            videoId = metadata.id,
                            audioQuality = AudioQuality.LOW,
                            connectivityManager = cm,
                        ).getOrThrow()
                    }
                    resolvedUrl = data.streamUrl
                    headers = data.streamHeaders
                }

                val builder = ExoPlayer.Builder(context)
                if (headers.isNotEmpty()) {
                    val httpFactory = DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(headers)
                        .setAllowCrossProtocolRedirects(true)
                    builder.setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                }
                val exo = builder.build().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        false,
                    )
                    var sought = false
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && !sought) {
                                sought = true
                                val dur = duration
                                if (dur > 45_000L) {
                                    seekTo((dur * 0.25f).toLong().coerceAtMost(35_000L))
                                }
                                loadingId = null
                                activeId = metadata.id
                                stopJob?.cancel()
                                stopJob = scope.launch {
                                    delay(PreviewDurationMs)
                                    stop(resumeMain = true)
                                }
                            } else if (state == Player.STATE_ENDED) {
                                stop(resumeMain = true)
                            }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            stop(resumeMain = true)
                            TopToaster.error("Couldn't preview this song")
                        }
                    })
                    setMediaItem(MediaItem.fromUri(resolvedUrl))
                    playWhenReady = true
                    prepare()
                }
                player = exo
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                stop(resumeMain = true)
                TopToaster.error("Couldn't preview this song")
            }
        }
    }

    fun stop(resumeMain: Boolean) {
        loadJob?.cancel()
        loadJob = null
        stopJob?.cancel()
        stopJob = null
        player?.runCatching {
            stop()
            release()
        }
        player = null
        loadingId = null
        activeId = null
        if (resumeMain && pausedMainPlayer) {
            pausedMainPlayer = false
            mainConnection?.player?.play()
        }
    }

    fun release() {
        stop(resumeMain = true)
    }
}

@Composable
internal fun rememberAddMusicPreviewPlayer(): AddMusicPreviewPlayer {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val mainConnection = LocalPlayerConnection.current
    val preview = remember(context, mainConnection) { AddMusicPreviewPlayer(context, scope, mainConnection) }
    DisposableEffect(preview) {
        onDispose { preview.release() }
    }
    return preview
}
