package com.fishking.core.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.HabitPeriod
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fishking.core.model.DailyReview
import com.fishking.core.model.LifeGoalWithEvents
import com.fishking.core.model.JournalTextSize
import com.fishking.core.model.JournalTextStyleSpan
import com.fishking.core.model.TodoPriority
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun DaveJournalLocation(
    locationName: String?,
    modifier: Modifier = Modifier,
) {
    if (locationName.isNullOrBlank()) return
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = DavePalette.Meta, modifier = Modifier.size(14.dp))
        Text(
            locationName,
            color = DavePalette.Ink.copy(alpha = .62f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DaveJournalLine(
    value: TextFieldValue,
    textColor: Long?,
    textSize: JournalTextSize,
    styleSpans: List<JournalTextStyleSpan>,
    selected: Boolean,
    focusRequestToken: Long,
    onSelected: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    maxHeight: Dp = 300.dp,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken > 0L) {
            focusRequester.requestFocus()
            bringIntoViewRequester.bringIntoView()
        }
    }
    val annotated = remember(value.text, textColor, textSize, styleSpans) {
        buildAnnotatedString {
            append(value.text)
            styleSpans.forEach { span ->
                if (span.start < span.endExclusive && span.endExclusive <= value.text.length) {
                    addStyle(
                        SpanStyle(
                            color = span.color?.let(::Color) ?: Color.Unspecified,
                            fontSize = span.textSize?.toSp() ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                        ),
                        span.start,
                        span.endExclusive,
                    )
                }
            }
        }
    }
    DaveSwipeDeleteContainer(onDelete = onDelete, modifier = modifier, textGestures = true) {
        BasicTextField(
            value = TextFieldValue(annotated, value.selection, value.composition),
            onValueChange = { next ->
                onValueChange(TextFieldValue(next.text, next.selection, next.composition))
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .heightIn(max = maxHeight)
                .background(
                    Color.White,
                    RoundedCornerShape(10.dp),
                )
                .then(
                    if (selected) Modifier.border(1.dp, DavePalette.Meta.copy(alpha = .55f), RoundedCornerShape(10.dp))
                    else Modifier,
                )
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .focusRequester(focusRequester)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        onSelected()
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
            textStyle = TextStyle(
                color = textColor?.let(::Color) ?: DavePalette.Ink,
                fontSize = textSize.toSp(),
                lineHeight = (maxOf(textSize.toSp().value, styleSpans.maxOfOrNull { it.textSize?.toSp()?.value ?: 0f } ?: 0f) * 1.45f).sp,
            ),
            cursorBrush = SolidColor(DavePalette.HeaderGreen),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.text.isEmpty()) Text("写点什么…", color = DavePalette.Ink.copy(alpha = .28f), fontSize = 17.sp)
                    input()
                }
            },
        )
    }
}

@Composable
fun DaveJournalTitle(value: TextFieldValue, color: Long?, textSize: JournalTextSize,
    styleSpans: List<JournalTextStyleSpan>, focusRequestToken: Long,
    onValueChange: (TextFieldValue) -> Unit, onSelected: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequestToken) { if (focusRequestToken > 0L) focusRequester.requestFocus() }
    val annotated = remember(value.text, styleSpans) {
        buildAnnotatedString {
            append(value.text)
            styleSpans.forEach { span ->
                if (span.start < span.endExclusive && span.endExclusive <= value.text.length) addStyle(
                    SpanStyle(color = span.color?.let(::Color) ?: Color.Unspecified,
                        fontSize = span.textSize?.toSp() ?: androidx.compose.ui.unit.TextUnit.Unspecified),
                    span.start, span.endExclusive)
            }
        }
    }
    BasicTextField(value = TextFieldValue(annotated, value.selection, value.composition),
        onValueChange = { onValueChange(TextFieldValue(it.text, it.selection, it.composition)) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onSelected() },
        textStyle = TextStyle(color = color?.let(::Color) ?: DavePalette.Ink, fontSize = textSize.toSp(),
            lineHeight = (maxOf(textSize.toSp().value, styleSpans.maxOfOrNull { it.textSize?.toSp()?.value ?: 0f } ?: 0f) * 1.45f).sp,
            fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(DavePalette.HeaderGreen),
        decorationBox = { input ->
            Box { if (value.text.isEmpty()) Text("日记标题", color = DavePalette.Meta.copy(alpha = .6f), fontSize = 24.sp); input() }
        })
}

@Composable
fun DaveJournalRecordingBar(
    elapsedMillis: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DavePalette.JournalPaper, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).background(DavePalette.Urgent, CircleShape))
        Text(
            formatDuration(elapsedMillis),
            color = DavePalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 9.dp),
        )
        JournalTool(Icons.Outlined.Close, "取消录音", onCancel)
        Spacer(Modifier.width(8.dp))
        JournalTool(Icons.Outlined.StopCircle, "停止并插入录音", onStop)
    }
}

@Composable
fun DaveSwipeDeleteContainer(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    textGestures: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidth = with(density) { 62.dp.toPx() }
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var contentHeight by remember { mutableStateOf(48.dp) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun settle(target: Float) {
        scope.launch {
            animate(offsetX, target, animationSpec = tween(170)) { value, _ -> offsetX = value }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX < -1f) Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(62.dp)
                .height(contentHeight)
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(DavePalette.Urgent)
                .clickable {
                    settle(0f)
                    onDelete()
                }
                .semantics { contentDescription = "删除这一项" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(10.dp))
                .background(DavePalette.JournalPaper)
                .onSizeChanged { contentHeight = with(density) { it.height.toDp() } }
                .then(if (textGestures) Modifier.daveTextSwipe(
                    onStart = { focusManager.clearFocus(); keyboard?.hide() },
                    onDelta = { amount -> offsetX = (offsetX + amount).coerceIn(-actionWidth, 0f) },
                    onEnd = { settle(if (offsetX <= -actionWidth * .42f) -actionWidth else 0f) },
                ) else Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offsetX = (offsetX + amount).coerceIn(-actionWidth, 0f)
                        },
                        onDragEnd = {
                            settle(if (offsetX <= -actionWidth * .42f) -actionWidth else 0f)
                        },
                        onDragCancel = { settle(0f) },
                    )
                }),
        ) { content() }
    }
}

private fun JournalTextSize.toSp() = when (this) {
    JournalTextSize.SMALL -> 14.sp
    JournalTextSize.BODY -> 18.sp
    JournalTextSize.LARGE -> 22.sp
    JournalTextSize.TITLE -> 28.sp
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
fun DaveJournalImageRow(
    paths: List<String>,
    onDeleteImage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    motionVideos: Map<String, String> = emptyMap(),
) {
    var fullScreenIndex by remember(paths) { mutableStateOf<Int?>(null) }
    var motionPlaying by remember(paths) { mutableStateOf<String?>(null) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .3f), RoundedCornerShape(12.dp))
            .padding(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            paths.take(3).forEachIndexed { index, path ->
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                    val decodedBitmap = withContext(Dispatchers.IO) { decodeSampled(path, 1_080) }
                    this.value = decodedBitmap
                }
                DaveSwipeDeleteContainer(onDelete = { onDeleteImage(index) }, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (paths.size == 1) 1.6f else 1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(DavePalette.CardMuted)
                        .clickable(enabled = bitmap != null) { fullScreenIndex = index },
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "日记图片，点击放大",
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentScale = ContentScale.Crop,
                        )
                    } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = DavePalette.Meta)
                    motionVideos[path]?.let { video ->
                        DaveMotionPhotoButton(onClick = { motionPlaying = video }, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                }
                }
            }
        }
    }
    fullScreenIndex?.let { index ->
        paths.getOrNull(index)?.let { path ->
            DaveFullScreenImage(
                path = path,
                motionVideo = motionVideos[path],
                onDismiss = { fullScreenIndex = null },
                onDelete = {
                    fullScreenIndex = null
                    onDeleteImage(index)
                },
            )
        }
    }
    motionPlaying?.let { video -> DaveVideoPlayerDialog(video, onDismiss = { motionPlaying = null }, closeWhenEnded = false) }
}

@Composable
fun DaveJournalTimelineThumbnails(
    paths: List<String>,
    modifier: Modifier = Modifier,
) {
    val visiblePaths = paths.filter(String::isNotBlank).distinct().take(3)
    if (visiblePaths.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        visiblePaths.forEachIndexed { index, path ->
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                value = withContext(Dispatchers.IO) { decodeSampled(path, 720) }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(DavePalette.JournalPaper),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "日记图片缩略图 ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = "日记图片缩略图加载中 ${index + 1}",
                        tint = DavePalette.Meta.copy(alpha = .5f),
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
fun DaveJournalGifBlock(
    path: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playing by remember(path) { mutableStateOf(false) }
    var fullScreen by remember(path) { mutableStateOf(false) }
    val decoded by produceState(AnimatedDecodeState(), path) {
        val decodedState = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 28) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) }
                    .fold(
                        onSuccess = {
                            AnimatedDecodeState(
                                drawable = it,
                                animated = it is AnimatedImageDrawable,
                                complete = true,
                            )
                        },
                        onFailure = { AnimatedDecodeState(error = "动态图片无法解码", complete = true) },
                    )
            } else AnimatedDecodeState(error = "此系统版本不支持播放动态图片", complete = true)
        }
        this.value = decodedState
    }
    val drawable = decoded.drawable
    DaveSwipeDeleteContainer(onDelete = onDelete, modifier = modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(DavePalette.CardMuted),
    ) {
        if (drawable != null) {
            AndroidView(
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view ->
                    view.setImageDrawable(drawable)
                    setAnimatedImagePlayback(drawable, playing)
                },
                modifier = Modifier.fillMaxWidth().fillMaxHeight().clickable { fullScreen = true },
            )
        } else {
            val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
                val decodedBitmap = withContext(Dispatchers.IO) { decodeSampled(path, 1_080) }
                this.value = decodedBitmap
            }
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "GIF 首帧，点击放大",
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().clickable { fullScreen = true },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            "动图",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .55f), CircleShape)
                .clickable(enabled = decoded.animated) { playing = !playing }
                .semantics { contentDescription = if (playing) "暂停动态图片" else "播放动态图片" },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (playing) "Ⅱ" else "▶", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (decoded.complete && !decoded.animated) {
            Text(
                decoded.error ?: "这张图片没有可播放的动画",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    }
    DisposableEffect(drawable) {
        onDispose { stopAnimatedImage(drawable) }
    }
    if (fullScreen) DaveFullScreenImage(path, onDismiss = { fullScreen = false })
}

private data class AnimatedDecodeState(
    val drawable: Drawable? = null,
    val animated: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false,
)

private fun setAnimatedImagePlayback(drawable: Drawable, playing: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (drawable as? AnimatedImageDrawable)?.let { animation ->
            if (playing) animation.start() else animation.stop()
        }
    }
}

private fun stopAnimatedImage(drawable: Drawable?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (drawable as? AnimatedImageDrawable)?.stop()
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
fun DaveJournalVideoBlock(
    path: String,
    durationMillis: Long?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fullScreen by remember(path) { mutableStateOf(false) }
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        val decodedThumbnail = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    retriever.getFrameAtTime(0L)?.asImageBitmap()
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
        this.value = decodedThumbnail
    }
    DaveSwipeDeleteContainer(onDelete = onDelete, modifier = modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable { fullScreen = true },
    ) {
        thumbnail?.let {
            Image(it, "视频封面，点击全屏播放", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            modifier = Modifier.align(Alignment.Center).size(54.dp).background(Color.Black.copy(alpha = .58f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        durationMillis?.let {
            Text(
                formatDuration(it),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
    }
    if (fullScreen) DaveFullScreenVideo(path = path, onDismiss = { fullScreen = false })
}

@Composable
fun DaveJournalAudioBlock(
    path: String,
    durationMillis: Long?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            prepare()
        }
    }
    var playing by remember(path) { mutableStateOf(false) }
    var position by remember(path) { mutableStateOf(0L) }
    var resolvedDuration by remember(path, durationMillis) { mutableStateOf(durationMillis ?: 0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val value = player.duration
                if (value > 0L) resolvedDuration = value
                if (playbackState == Player.STATE_ENDED) position = resolvedDuration
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, playing) {
        while (isActive && playing) {
            position = player.currentPosition.coerceAtLeast(0L)
            val value = player.duration
            if (value > 0L) resolvedDuration = value
            delay(200L)
        }
    }
    DaveSwipeDeleteContainer(onDelete = onDelete, modifier = modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .38f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DavePalette.HeaderGreen, CircleShape)
                .clickable {
                    if (playing) player.pause() else {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                        player.play()
                    }
                }
                .semantics { contentDescription = if (playing) "暂停录音" else "播放录音" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Slider(
                value = position.coerceIn(0L, resolvedDuration.coerceAtLeast(1L)).toFloat(),
                onValueChange = {
                    position = it.toLong()
                    player.seekTo(position)
                },
                valueRange = 0f..resolvedDuration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
            Text(
                "${formatDuration(position)} / ${formatDuration(resolvedDuration)}",
                color = DavePalette.Ink.copy(alpha = .62f),
                fontSize = 11.sp,
            )
        }
    }
    }
}

@Composable
@SuppressLint("ProduceStateDoesNotAssignValue")
private fun DaveFullScreenImage(path: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, motionVideo: String? = null) {
    var motionPlaying by remember(path) { mutableStateOf(false) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        val decodedBitmap = withContext(Dispatchers.IO) { decodeSampled(path, 2_400) }
        this.value = decodedBitmap
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black, RoundedCornerShape(12.dp))
                .clickable { if (scale == 1f) onDismiss() }
                .transformable(transform),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "放大的日记图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                    contentScale = ContentScale.Fit,
                )
            }
            onDelete?.let { delete ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DavePalette.Urgent.copy(alpha = .9f), CircleShape)
                        .clickable(onClick = delete)
                        .semantics { contentDescription = "删除当前照片" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            if (motionVideo != null) DaveMotionPhotoButton(onClick = { motionPlaying = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
    if (motionPlaying && motionVideo != null) DaveVideoPlayerDialog(motionVideo,
        onDismiss = { motionPlaying = false }, closeWhenEnded = false)
}

@Composable
private fun DaveFullScreenVideo(path: String, onDismiss: () -> Unit) {
    DaveVideoPlayerDialog(path, onDismiss)
}

private fun decodeSampled(path: String, maxEdge: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bitmap.asImageBitmap()
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
fun DaveReviewToggleButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (expanded) DavePalette.HeaderGreen else DavePalette.Card.copy(alpha = .86f),
                CircleShape,
            )
            .border(1.dp, DavePalette.HeaderGreenDark.copy(alpha = .55f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (expanded) "收起当天已办与打卡" else "显示当天已办与打卡" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Checklist,
            null,
            tint = if (expanded) Color.White else DavePalette.HeaderGreenDark,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun JournalTool(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(37.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Color.White.copy(alpha = .52f) else Color.White.copy(alpha = .2f),
                CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = DavePalette.Ink.copy(alpha = if (enabled) .78f else .28f), modifier = Modifier.size(21.dp))
    }
}

@Composable
fun DaveEmojiPalette(
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember {
        listOf(
            "😀" to listOf("😀", "😄", "😂", "🥹", "😊", "😍", "🥰", "😎", "🤔", "😴", "😤", "😭", "😱", "🤯", "🥳", "🫠"),
            "👍" to listOf("👍", "👎", "👏", "🙌", "🙏", "💪", "🤝", "✌️", "🤞", "👌", "👀", "🫶", "❤️", "💔", "💯", "✨"),
            "🌿" to listOf("🌿", "🌱", "🌸", "🌈", "☀️", "🌙", "⭐", "🔥", "💧", "❄️", "🐟", "🐱", "🐶", "🦋", "🌊", "⛰️"),
            "🍜" to listOf("🍜", "🍚", "🍔", "🍕", "🥗", "🍰", "☕", "🍺", "🍎", "🍓", "🥑", "🍗", "🥟", "🍣", "🥛", "🧋"),
            "🎯" to listOf("🎯", "✅", "❌", "⚠️", "💡", "📌", "📝", "📷", "🎵", "🎬", "🎮", "🏃", "🚴", "✈️", "🏠", "🎁"),
        )
    }
    var selectedGroup by remember { mutableStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DavePalette.Card.copy(alpha = .96f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            groups.forEachIndexed { index, group ->
                Text(
                    group.first,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .background(
                            if (selectedGroup == index) DavePalette.HeaderGreen.copy(alpha = .2f) else Color.Transparent,
                            CircleShape,
                        )
                        .clip(CircleShape)
                        .clickable { selectedGroup = index }
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups[selectedGroup].second.forEach { emoji ->
                Text(
                    emoji,
                    fontSize = 24.sp,
                    modifier = Modifier.clip(CircleShape).clickable { onSelected(emoji) }.padding(horizontal = 3.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun DaveTextColorPalette(
    selected: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DaveAccentPalette(selected = selected, onSelected = onSelected, modifier = modifier)
}

@Composable
fun DaveTextSizePalette(
    selected: JournalTextSize?,
    onSelected: (JournalTextSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DavePalette.Card.copy(alpha = .96f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            JournalTextSize.SMALL to "小",
            JournalTextSize.BODY to "正文",
            JournalTextSize.LARGE to "大",
            JournalTextSize.TITLE to "标题",
        ).forEach { (size, label) ->
            Text(
                label,
                color = DavePalette.Ink,
                fontSize = size.toSp(),
                fontWeight = if (selected == size) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .background(
                        if (selected == size) DavePalette.HeaderGreen.copy(alpha = .2f) else Color.Transparent,
                        RoundedCornerShape(7.dp),
                    )
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelected(size) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun DaveLocationEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onLocate: () -> Unit,
    locating: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .35f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).background(DavePalette.Card, RoundedCornerShape(8.dp)).padding(10.dp),
            singleLine = true,
            textStyle = TextStyle(color = DavePalette.Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(DavePalette.HeaderGreen),
            decorationBox = { input ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) Text("手动输入这篇日记的位置", color = DavePalette.Ink.copy(alpha = .34f), fontSize = 14.sp)
                    input()
                }
            },
        )
        if (locating) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp).size(28.dp),
                strokeWidth = 2.dp,
                color = DavePalette.Meta,
            )
        } else {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DavePalette.Meta, CircleShape)
                    .clickable(onClick = onLocate)
                    .semantics { contentDescription = "使用当前位置" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MyLocation, null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
        }
        Box(
            modifier = Modifier.padding(start = 8.dp).size(38.dp).clip(CircleShape).background(DavePalette.Completed, CircleShape).clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DaveGoalPicker(
    goals: List<LifeGoalWithEvents>,
    linkedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = .34f), RoundedCornerShape(10.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("关联人生目标", color = DavePalette.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "勾选后，可从对应目标直接回到这一天的日记",
            color = DavePalette.Ink.copy(alpha = .55f),
            fontSize = 12.sp,
        )
        if (goals.isEmpty()) {
            Text("还没有可关联的人生目标", color = DavePalette.Ink.copy(alpha = .5f), fontSize = 13.sp)
        } else {
            goals.forEach { item ->
                val linked = item.goal.id in linkedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (linked) DavePalette.HeaderGreen.copy(alpha = .18f) else DavePalette.Card, RoundedCornerShape(8.dp))
                        .clickable { onToggle(item.goal.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(22.dp).border(2.dp, if (linked) DavePalette.Completed else DavePalette.Ink.copy(alpha = .5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (linked) Text("✓", color = DavePalette.Completed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(item.goal.title, color = DavePalette.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 9.dp))
                    if (linked) Icon(Icons.Outlined.Close, "取消关联此目标", tint = DavePalette.Meta,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onToggle(item.goal.id) })
                }
            }
        }
    }
}

@Composable
fun DaveDailyReviewPanel(
    review: DailyReview,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val checkedHabits = review.checkedHabits.sortedBy { it.position }
        @Composable fun HabitSummary(habit: com.fishking.core.model.HabitDaySummary) {
            DaveHabitCard(
                title = habit.title,
                count = habit.displayCount,
                targetCount = habit.targetCount,
                period = habit.period,
                color = habit.color,
                isBackfilled = habit.isBackfilled,
                checkedOnDate = habit.count > 0,
                onClick = {},
                readOnly = true,
            )
        }
        checkedHabits.filter { it.period == HabitPeriod.DAILY && it.count < it.targetCount }.forEach { HabitSummary(it) }
        review.completedTodos.sortedWith(
            compareBy<com.fishking.core.model.TodoOccurrence> {
                if (it.priority == TodoPriority.URGENT) 0 else 1
            }.thenBy { it.position },
        ).forEach { todo ->
            DaveTaskCard(todo = todo, onToggleCompletion = {}, readOnly = true)
        }
        checkedHabits.filter { it.period == HabitPeriod.WEEKLY || it.count >= it.targetCount }.forEach { HabitSummary(it) }
    }
}
