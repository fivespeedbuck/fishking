package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
internal fun DaveMotionPhotoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .68f), RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp)
        .semantics { contentDescription = "播放动态图" }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text("动态图", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
internal fun DaveVideoPlayerDialog(path: String, onDismiss: () -> Unit, closeWhenEnded: Boolean = false) {
    val context = LocalContext.current
    val dismiss by rememberUpdatedState(onDismiss)
    var fullScreen by remember { mutableStateOf(false) }
    val shouldCloseAtEnd by rememberUpdatedState(closeWhenEnded && !fullScreen)
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember(path) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
        prepare(); playWhenReady = false
    } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0)
                if (playbackState == Player.STATE_ENDED) {
                    position = duration
                    if (shouldCloseAtEnd) dismiss()
                }
            }
            override fun onPlayerError(failure: androidx.media3.common.PlaybackException) {
                error = "视频暂不能播放（${failure.errorCodeName}）"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, playing) {
        while (isActive) { position = player.currentPosition; duration = player.duration.coerceAtLeast(0); delay(150) }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !fullScreen, decorFitsSystemWindows = !fullScreen)) {
        Column((if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .background(Color.Black, RoundedCornerShape(if (fullScreen) 0.dp else 12.dp))
            .then(if (fullScreen) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭视频", tint = Color.White) }
            }
            Box(if (fullScreen) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { useController = false } },
                    update = { it.player = player }, modifier = Modifier.fillMaxSize())
                error?.let { Text(it, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 13.sp) }
            }
            DaveVideoControls(playing, position, duration, fullScreen,
                onPlayPause = { if (playing) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
                onSeek = { position = it; player.seekTo(it) }, onFullScreen = { fullScreen = !fullScreen })
        }
    }
}

@Composable
internal fun DaveVideoControls(playing: Boolean, position: Long, duration: Long, fullScreen: Boolean,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onFullScreen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Color(0xFF171D20)).padding(horizontal = 6.dp, vertical = 4.dp)
        .semantics { contentDescription = "视频底部控制栏" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (playing) "暂停视频" else "播放视频", tint = Color.White) }
            Slider(value = position.coerceIn(0, duration.coerceAtLeast(1)).toFloat(), onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(), modifier = Modifier.weight(1f).semantics { contentDescription = "视频进度" })
            IconButton(onClick = onFullScreen) { Icon(if (fullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                if (fullScreen) "退出全屏" else "全屏播放", tint = Color.White) }
        }
        fun time(value: Long) = "%d:%02d".format(value.coerceAtLeast(0) / 60000, value.coerceAtLeast(0) / 1000 % 60)
        Text("${time(position)} / ${time(duration)}", color = Color.White.copy(alpha = .8f), fontSize = 11.sp,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
    }
}
