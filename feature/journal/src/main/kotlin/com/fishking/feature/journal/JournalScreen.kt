package com.fishking.feature.journal

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import com.fishking.core.ui.DavePalette
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishking.core.ui.DaveJournalLine
import com.fishking.core.ui.DaveJournalImageRow
import com.fishking.core.ui.DaveJournalGifBlock
import com.fishking.core.ui.DaveJournalVideoBlock
import com.fishking.core.ui.DaveJournalAudioBlock
import com.fishking.core.ui.DaveJournalLocation
import com.fishking.core.ui.DaveJournalRecordingBar
import com.fishking.core.ui.DaveJournalFormatStrip
import com.fishking.core.ui.DaveJournalActionBar
import com.fishking.core.ui.DaveLocationEditor
import com.fishking.core.ui.LocalPresetTags
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.media.JournalMediaStore
import com.fishking.core.media.JournalAudioRecorder
import com.fishking.core.location.JournalLocationProvider
import com.fishking.core.model.JournalBlockType
import java.time.LocalDate
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit

@Composable
fun JournalScreen(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit = {},
    journalRepository: JournalRepository,
    dailyReviewRepository: DailyReviewRepository,
    lifeRepository: LifeRepository,
    mediaStore: JournalMediaStore,
    audioRecorder: JournalAudioRecorder,
    locationProvider: JournalLocationProvider,
    modifier: Modifier = Modifier,
    journalEntryKey: String? = null,
    viewModel: JournalViewModel = viewModel(
        key = journalEntryKey,
        factory = JournalViewModel.factory(
            journalRepository,
            mediaStore,
            audioRecorder,
            locationProvider,
            dailyReviewRepository,
            lifeRepository,
        ),
    ),
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val editorItems by viewModel.items.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.selectedIndex.collectAsStateWithLifecycle()
    val importingMedia by viewModel.importingMedia.collectAsStateWithLifecycle()
    val mediaImportError by viewModel.mediaImportError.collectAsStateWithLifecycle()
    val tagSaveError by viewModel.tagSaveError.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingElapsedMillis by viewModel.recordingElapsedMillis.collectAsStateWithLifecycle()
    val locationEditorExpanded by viewModel.locationEditorExpanded.collectAsStateWithLifecycle()
    val locationDraft by viewModel.locationDraft.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val focusRequest by viewModel.focusRequest.collectAsStateWithLifecycle()
    val undoNotice by viewModel.undoNotice.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val visibleItems = if (!loading && viewModel.isReady(selectedDate)) editorItems else emptyList()
    var linkPickerExpanded by remember(selectedDate) { mutableStateOf(false) }
    var tagPickerExpanded by remember(selectedDate) { mutableStateOf(false) }
    var tagDraft by remember(selectedDate) { mutableStateOf("") }
    var fullScreenEditor by remember(selectedDate) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val currentDate by rememberUpdatedState(selectedDate)
    val currentDateChange by rememberUpdatedState(onDateChange)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxPullPx = with(density) { 104.dp.toPx() }
    var edgePull by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val edgeConnection = remember(listState, thresholdPx, maxPullPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && edgePull != 0f && available.y * edgePull < 0f) {
                    val previous = edgePull
                    edgePull = if (abs(available.y) >= abs(previous)) 0f else previous + available.y
                    return Offset(0f, edgePull - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = !listState.canScrollBackward && available.y > 0f
                val atBottom = !listState.canScrollForward && available.y < 0f
                if (atTop || atBottom) edgePull = (edgePull + available.y * .42f).coerceIn(-maxPullPx, maxPullPx)
                if (atTop || atBottom) return available.copy(x = 0f)
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val released = edgePull
                if (abs(released) >= thresholdPx) {
                    val direction = if (released < 0f) 1 else -1
                    val travel = viewportHeightPx.takeIf { it > maxPullPx } ?: maxPullPx * 4f
                    val outgoing = if (direction > 0) -travel else travel
                    animate(released, outgoing, animationSpec = tween(145)) { value, _ -> edgePull = value }
                    currentDateChange(currentDate.plusDays(direction.toLong()))
                    listState.scrollToItem(0)
                    androidx.compose.runtime.withFrameNanos { }
                    edgePull = -outgoing
                }
                animate(edgePull, 0f, animationSpec = tween(if (abs(released) >= thresholdPx) 230 else 220)) { value, _ -> edgePull = value }
                return if (released != 0f) available else Velocity.Zero
            }
        }
    }

    LaunchedEffect(selectedDate) { viewModel.setDate(selectedDate) }
    val selectedColor = (editorItems.getOrNull(selectedIndex) as? JournalEditorItem.Text)?.color
    val importSelectedMedia: (List<android.net.Uri>) -> Unit = remember(context, viewModel) {{ uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        viewModel.importMedia(uris)
    }}
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
        onResult = importSelectedMedia,
    )
    val documentMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = importSelectedMedia,
    )
    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) viewModel.startRecording() else viewModel.reportAudioPermissionDenied()
        },
    )
    val locationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (granted) viewModel.useCurrentLocation() else viewModel.reportLocationPermissionDenied()
        },
    )
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.persistImmediately()
                viewModel.cancelRecording()
                viewModel.cancelLocationLookup()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.clearFocusRequest()
            viewModel.persistImmediately()
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelRecording()
            viewModel.cancelLocationLookup()
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize().navigationBarsPadding().imePadding()
        .background(DavePalette.JournalPaper)) {
        val bodyMinHeight = if (fullScreenEditor) (maxHeight - 150.dp).coerceAtLeast(260.dp) else 132.dp
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                .then(if (journalEntryKey == null) Modifier.nestedScroll(edgeConnection) else Modifier)
                .graphicsLayer { translationY = edgePull },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 82.dp),
        ) {
            if (!fullScreenEditor) item(key = "journal-location") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                    val titleIndex = visibleItems.indexOfFirst { it is JournalEditorItem.Text && it.isTitle }
                    val title = visibleItems.getOrNull(titleIndex) as? JournalEditorItem.Text
                    com.fishking.core.ui.DaveJournalTitle(title?.value ?: TextFieldValue(""), title?.color,
                        title?.textSize ?: com.fishking.core.model.JournalTextSize.TITLE, title?.styleSpans.orEmpty(),
                        if (titleIndex >= 0 && focusRequest.index == titleIndex) focusRequest.token else 0L,
                        viewModel::updateTitle, { if (titleIndex >= 0) viewModel.selectItem(titleIndex) })
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.HorizontalDivider(color = DavePalette.Meta.copy(alpha = .45f))
                }
                DaveJournalLocation(document?.entry?.takeIf { it.entryDate == selectedDate }?.locationName)
                document?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 5.dp)
                            .clickable {
                                tagDraft = tags.joinToString(" ")
                                tagPickerExpanded = true
                            },
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                color = DavePalette.HeaderGreenDark,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    .background(DavePalette.Card)
                                    .border(
                                        1.dp,
                                        DavePalette.Meta.copy(alpha = .45f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            item(key = "journal-format") {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().background(DavePalette.JournalPaper),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DaveJournalFormatStrip(
                        selectedColor = selectedColor,
                        selectedSize = (editorItems.getOrNull(selectedIndex) as? JournalEditorItem.Text)?.textSize,
                        onColor = viewModel::setTextColor,
                        onSize = viewModel::setTextSize,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (!fullScreenEditor && (editorItems.getOrNull(selectedIndex) as? JournalEditorItem.Text)?.isTitle == true) {
                                editorItems.indexOfFirst { it is JournalEditorItem.Text && !it.isTitle }
                                    .takeIf { it >= 0 }
                                    ?.let(viewModel::selectItem)
                            }
                            fullScreenEditor = !fullScreenEditor
                        },
                        modifier = Modifier.background(DavePalette.JournalPaper),
                    ) {
                        androidx.compose.material3.Icon(
                            if (fullScreenEditor) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            if (fullScreenEditor) "退出全屏编辑" else "全屏编辑",
                            tint = DavePalette.HeaderGreenDark,
                        )
                    }
                }
            }
            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DavePalette.Meta)
                }
            }
            itemsIndexed(visibleItems, key = { _, item -> item.editorKey }) { index, item ->
                if (!fullScreenEditor || selectedIndex == index) when (item) {
                    is JournalEditorItem.Text -> if (!item.isTitle) DaveJournalLine(
                        value = item.value,
                        textColor = item.color,
                        textSize = item.textSize,
                        styleSpans = item.styleSpans,
                        selected = selectedIndex == index,
                        focusRequestToken = if (focusRequest.index == index) focusRequest.token else 0L,
                        onSelected = { viewModel.selectItem(index) },
                        onValueChange = { value -> viewModel.updateText(index, value) },
                        onDelete = { viewModel.removeTextBlock(index) },
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        minHeight = bodyMinHeight,
                        maxHeight = if (fullScreenEditor) maxHeight else 300.dp,
                    )
                    is JournalEditorItem.Media -> when (item.type) {
                        JournalBlockType.IMAGE -> {
                            val motionVideos by androidx.compose.runtime.produceState<Map<String, String>>(emptyMap(), item.assets) {
                                value = item.assets.mapNotNull { asset -> mediaStore.motionVideoPath(asset.privatePath)?.let { asset.privatePath to it } }.toMap()
                            }
                            DaveJournalImageRow(
                            paths = item.assets.map { it.privatePath },
                            motionVideos = motionVideos,
                            onDeleteImage = { assetIndex -> viewModel.removeImage(index, assetIndex) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                        }
                        JournalBlockType.GIF -> DaveJournalGifBlock(
                            path = item.assets.single().privatePath,
                            onDelete = { viewModel.removeMediaBlock(index) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                        JournalBlockType.VIDEO -> DaveJournalVideoBlock(
                            path = item.assets.single().privatePath,
                            durationMillis = item.assets.single().durationMillis,
                            onDelete = { viewModel.removeMediaBlock(index) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                        JournalBlockType.AUDIO -> DaveJournalAudioBlock(
                            path = item.assets.single().privatePath,
                            durationMillis = item.assets.single().durationMillis,
                            onDelete = { viewModel.removeMediaBlock(index) },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                        else -> Unit
                    }
                }
            }
            val linkedGoals = goals.filter { it.goal.id in document?.linkedGoalIds.orEmpty() }
            if (!fullScreenEditor && linkedGoals.isNotEmpty()) item(key = "linked-goals") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("已关联人生清单", color = DavePalette.Meta)
                    linkedGoals.forEach { item ->
                        val goalShape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(DavePalette.Life.copy(alpha = .13f), goalShape)
                            .border(1.dp, DavePalette.Life.copy(alpha = .34f), goalShape)
                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(item.goal.title, color = DavePalette.Ink, modifier = Modifier.weight(1f))
                            Text(if (item.currentResult == com.fishking.core.model.LifeGoalResult.CHECK) "✓" else "○", color = DavePalette.Life)
                            androidx.compose.material3.TextButton(onClick = { viewModel.toggleGoal(item.goal.id) }) { Text("解除关联", color = DavePalette.Life) }
                        }
                    }
                }
            }
            val linkedTodos = (review?.completedTodos.orEmpty() + review?.openTodos.orEmpty())
                .filter { it.id in document?.linkedTodoIds.orEmpty() }
            if (!fullScreenEditor && linkedTodos.isNotEmpty()) item(key = "linked-todos") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)) {
                    Text("已关联待办", color = DavePalette.Meta)
                    linkedTodos.forEach { todo ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .background(DavePalette.CurrentWeek, androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (todo.isCompleted) "✓" else "○", color = if (todo.isCompleted) DavePalette.Completed else DavePalette.Meta)
                            Text(todo.title, color = DavePalette.Ink, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            androidx.compose.material3.TextButton(onClick = { viewModel.toggleTodo(todo.id) }) { Text("解除", color = DavePalette.Meta) }
                        }
                    }
                }
            }
            if (!fullScreenEditor && locationEditorExpanded) {
                item(key = "location-editor") {
                    DaveLocationEditor(
                        value = locationDraft,
                        onValueChange = viewModel::updateLocation,
                        onLocate = {
                            val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED
                            val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED
                            if (coarseGranted || fineGranted) {
                                viewModel.useCurrentLocation()
                            } else {
                                locationPermission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                        },
                        locating = locating,
                        onConfirm = viewModel::saveLocation,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    )
                }
            }
            if (!fullScreenEditor && linkPickerExpanded) item(key = "link-picker") {
                JournalLinkPicker(
                    goals = goals,
                    review = review,
                    linkedGoalIds = document?.linkedGoalIds.orEmpty().toSet(),
                    linkedTodoIds = document?.linkedTodoIds.orEmpty().toSet(),
                    onToggleGoal = viewModel::toggleGoal,
                    onToggleTodo = viewModel::toggleTodo,
                    onClose = { linkPickerExpanded = false },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            mediaImportError?.let { message ->
                item(key = "media-import-error") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp)) {
                        Text(message, color = DavePalette.Urgent)
                        androidx.compose.material3.TextButton(
                            onClick = { documentMediaPicker.launch(arrayOf("image/*", "video/*")) },
                        ) { Text("换用文件选择器重试", color = DavePalette.HeaderGreenDark) }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (!loading && viewModel.isReady(selectedDate)) Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            undoNotice?.let { notice ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DavePalette.Ink.copy(alpha = .92f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(notice.message, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = viewModel::undoDeletion) { Text("撤销", color = androidx.compose.ui.graphics.Color.White) }
                    androidx.compose.material3.IconButton(onClick = viewModel::dismissUndo) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Close, "关闭", tint = androidx.compose.ui.graphics.Color.White)
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            tagSaveError?.let { message ->
                Text(
                    message,
                    color = DavePalette.Urgent,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (tagPickerExpanded) {
                val presets = LocalPresetTags.current
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(DavePalette.Card)
                        .border(
                            1.dp,
                            DavePalette.Divider,
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("日记 TAG", color = DavePalette.Ink, modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { tagPickerExpanded = false }) {
                            androidx.compose.material3.Icon(Icons.Outlined.Close, "收起TAG编辑", tint = DavePalette.Meta)
                        }
                    }
                    if (presets.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                        ) {
                            presets.forEach { tag ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        val selected = parseJournalTags(tagDraft).toMutableList()
                                        val existing = selected.indexOfFirst { it.equals(tag, ignoreCase = true) }
                                        if (existing >= 0) selected.removeAt(existing) else selected += tag
                                        tagDraft = selected.joinToString(" ")
                                    },
                                ) {
                                    Text("#$tag", color = DavePalette.Meta)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(
                            value = tagDraft,
                            onValueChange = { tagDraft = it.replace('\n', ' ') },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("例如：生活 旅行", color = DavePalette.Meta.copy(alpha = .6f)) },
                        )
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.setTags(tagDraft)
                                tagPickerExpanded = false
                            },
                        ) {
                            Text("应用", color = DavePalette.HeaderGreenDark)
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
            if (isRecording) DaveJournalRecordingBar(
                elapsedMillis = recordingElapsedMillis,
                onStop = viewModel::stopRecording,
                onCancel = viewModel::cancelRecording,
            ) else DaveJournalActionBar(
                onLocation = {
                    viewModel.toggleLocationEditor()
                },
                onLink = {
                    linkPickerExpanded = !linkPickerExpanded
                },
                onTags = {
                    if (!tagPickerExpanded) tagDraft = document?.tags.orEmpty().joinToString(" ")
                    tagPickerExpanded = !tagPickerExpanded
                },
                onMedia = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onRecord = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startRecording()
                    } else {
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                importingMedia = importingMedia,
                hasTags = document?.tags?.isNotEmpty() == true,
            )
        }
    }
}

@Composable
private fun JournalLinkPicker(
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    review: com.fishking.core.model.DailyReview?,
    linkedGoalIds: Set<String>,
    linkedTodoIds: Set<String>,
    onToggleGoal: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(DavePalette.Card, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("关联内容", color = DavePalette.Ink, modifier = Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = onClose) { androidx.compose.material3.Icon(Icons.Outlined.Close, "关闭关联", tint = DavePalette.Meta) }
        }
        goals.forEach { item -> LinkChoice(item.goal.title, item.goal.id in linkedGoalIds) { onToggleGoal(item.goal.id) } }
        review?.completedTodos.orEmpty().forEach { todo -> LinkChoice("已完成 · ${todo.title}", todo.id in linkedTodoIds) { onToggleTodo(todo.id) } }
        review?.openTodos.orEmpty().forEach { todo -> LinkChoice("未完成 · ${todo.title}", todo.id in linkedTodoIds) { onToggleTodo(todo.id) } }
        if (goals.isEmpty() && review?.completedTodos.orEmpty().isEmpty() && review?.openTodos.orEmpty().isEmpty()) {
            Text("当天暂无可关联的人生目标或待办", color = DavePalette.Meta)
        }
    }
}

@Composable
private fun LinkChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(if (selected) DavePalette.HeaderGreen.copy(alpha = .15f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓" else "○", color = DavePalette.HeaderGreenDark, modifier = Modifier.padding(end = 8.dp))
        Text(label, color = DavePalette.Ink)
    }
}
