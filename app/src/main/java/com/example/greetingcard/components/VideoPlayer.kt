package com.example.greetingcard.components

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        // Optimize for ultra-low latency
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 1000, 250, 500)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("VideoPlayer", "Error streaming $url: ${error.message}")
                        statusMessage = if (error.message?.contains("404") == true || error.cause?.message?.contains("404") == true) {
                            "Waiting for stream..."
                        } else {
                            "Connecting..."
                        }
                        isBuffering = false
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) {
                            statusMessage = null
                        }
                    }
                })
            }
    }

    // Connect logic
    LaunchedEffect(url, retryCount, enabled) {
        if (!enabled) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            statusMessage = "Robot Sleeping"
            isBuffering = false
            return@LaunchedEffect
        }

        statusMessage = "Connecting..."
        isBuffering = true
        
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.Builder()
                .setUri(url)
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(0).build())
                .build())
            
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Autonomous Retry Loop
    LaunchedEffect(statusMessage, enabled) {
        if (enabled && statusMessage != null) {
            delay(2000)
            retryCount++
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = modifier.background(Color.Black, RoundedCornerShape(8.dp))) {
        if (enabled) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering || (enabled && statusMessage != null)) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).align(Alignment.Center),
                color = Color.White,
                strokeWidth = 2.dp
            )
        }

        statusMessage?.let { msg ->
            Text(
                text = msg,
                color = if (msg.contains("Sleeping")) Color.Gray else Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}
