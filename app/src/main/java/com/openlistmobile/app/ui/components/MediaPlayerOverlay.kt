package com.openlistmobile.app.ui.components

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import com.openlistmobile.app.service.MediaPlaybackService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerOverlay(
    url: String?,
    fileName: String?,
    isAudio: Boolean,
    onDismiss: () -> Unit
) {
    if (url == null) return

    val context = LocalContext.current
    var controller by remember(url) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(url) {
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            val c = future.get()
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(fileName ?: "Media")
                        .build()
                )
                .build()
            c.setMediaItem(mediaItem)
            c.prepare()
            c.playWhenReady = true
            controller = c
        }, MoreExecutors.directExecutor())

        onDispose {
            val c = controller
            if (c != null) {
                if (!isAudio) {
                    c.stop()
                    c.clearMediaItems()
                }
                c.release()
            } else {
                future.cancel(true)
            }
            controller = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (isAudio) MaterialTheme.colorScheme.surface else Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = fileName ?: "Media",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isAudio) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭",
                                tint = if (isAudio) MaterialTheme.colorScheme.onSurface else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isAudio) MaterialTheme.colorScheme.surface else Color.Black.copy(alpha = 0.6f)
                    )
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val c = controller
                    if (c == null) {
                        Text(
                            text = "正在准备播放...",
                            modifier = Modifier.align(Alignment.Center),
                            color = if (isAudio) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        )
                    } else if (isAudio) {
                        AudioPlayerContent(controller = c, fileName = fileName)
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            update = { view -> view.player = c }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerContent(controller: MediaController, fileName: String?) {
    var isPlaying by remember { mutableStateOf(controller.isPlaying) }
    var position by remember { mutableLongStateOf(controller.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(controller.duration.coerceAtLeast(0L)) }
    var isSeeking by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = controller.duration
                if (d > 0) duration = d
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            if (!isSeeking) {
                position = controller.currentPosition.coerceAtLeast(0L)
                val d = controller.duration
                if (d > 0) duration = d
            }
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(96.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = fileName ?: "",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(24.dp))

        val safeDuration = if (duration > 0) duration else 1L
        Slider(
            value = position.coerceIn(0L, safeDuration).toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            onValueChange = {
                isSeeking = true
                position = it.toLong()
            },
            onValueChangeFinished = {
                controller.seekTo(position)
                isSeeking = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatMillis(position), style = MaterialTheme.typography.bodySmall)
            Text(text = formatMillis(duration), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.seekTo((controller.currentPosition - 10_000).coerceAtLeast(0L)) }) {
                Icon(Icons.Rounded.FastRewind, contentDescription = "后退 10 秒", modifier = Modifier.size(40.dp))
            }
            IconButton(
                onClick = {
                    if (controller.isPlaying) controller.pause() else controller.play()
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = { controller.seekTo((controller.currentPosition + 10_000).coerceAtMost(controller.duration)) }) {
                Icon(Icons.Rounded.FastForward, contentDescription = "前进 10 秒", modifier = Modifier.size(40.dp))
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
