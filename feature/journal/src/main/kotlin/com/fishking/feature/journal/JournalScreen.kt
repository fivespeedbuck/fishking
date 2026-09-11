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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fishking.core.ui.DaveJournalLine
import com.fishking.core.ui.DaveJournalTitle
import com.fishking.core.ui.DaveJournalImageRow
import com.fishking.core.ui.DaveJournalGifBlock
import com.fishking.core.ui.DaveJournalVideoBlock
import com.fishking.core.ui.DaveJournalAudioBlock
import com.fishking.core.ui.DaveJournalLocation
import com.fishking.core.ui.DaveJournalRecordingBar
import com.fishking.core.ui.DaveJournalFormatStrip
import com.fishking.core.ui.DaveLocationEditor
import com.fishking.core.ui.DaveSwipeDeleteContainer
import com.fishking.core.usecase.DailyReviewRepository
import com.fishking.core.usecase.JournalRepository
import com.fishking.core.usecase.LifeRepository
import com.fishking.core.media.JournalMediaStore
import com.fishking.core.media.JournalAudioRecorder
import com.fishking.core.location.JournalLocationProvider
import com.fishking.core.model.JournalBlockType
import com.fishking.core.model.JournalTextAlignment
import com.fishking.core.model.JournalDocumentBuffer
import java.time.LocalDate
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
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
    onClose: () -> Unit = {},
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
    val readError by viewModel.readError.collectAsStateWithLifecycle()
    val tagSaveError by viewModel.tagSaveError.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingElapsedMillis by viewModel.recordingElapsedMillis.collectAsStateWithLifecycle()
    val locationEditorExpanded by viewModel.locationEditorExpanded.collectAsStateWithLifecycle()
    val locationDraft by viewModel.locationDraft.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val focusRequest by viewModel.focusRequest.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val visibleItems = if (!loading && viewModel.isReady(selectedDate)) editorItems else emptyList()
    // Journals are edited in-place as a full-screen document. The old
    // read-only preview required a second tap before the text field could be
    // used, which also made media deletion effectively undiscoverable.
    var fullScreenEditor by remember(selectedDate) { mutableStateOf(true) }
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
    readError?.let { message ->
        Column(modifier.fillMaxSize().navigationBarsPadding().padding(24.dp)) {
            androidx.compose.material3.TextButton(onClick = onClose) {
                Text("返回日记时间线", color = DavePalette.HeaderGreenDark)
            }
            Text(message, color = DavePalette.Urgent, modifier = Modifier.padding(vertical = 16.dp))
            androidx.compose.material3.TextButton(onClick = viewModel::retryRead) { Text("重试") }
        }
        return
    }
    if (fullScreenEditor && !loading && viewModel.isReady(selectedDate)) {
        JournalFullscreenEditor(
            items = visibleItems,
            selectedIndex = selectedIndex,
            focusRequest = focusRequest,
            document = document,
            goals = goals,
            review = review,
            locationEditorExpanded = locationEditorExpanded,
            locationDraft = locationDraft,
            locating = locating,
            importingMedia = importingMedia,
            mediaImportError = mediaImportError,
            isRecording = isRecording,
            recordingElapsedMillis = recordingElapsedMillis,
            tagSaveError = tagSaveError,
            canUndo = viewModel.canUndoEdit(),
            canRedo = viewModel.canRedoEdit(),
            // The journal entry itself is now the editor. Back / Done return
            // directly to the timeline instead of revealing an intermediate
            // read-only copy that needs another tap.
            onBack = { viewModel.persistImmediately(); onClose() },
            onDone = { viewModel.persistImmediately(); onClose() },
            onUndo = viewModel::undoEdit,
            onRedo = viewModel::redoEdit,
             onRequestFocus = viewModel::requestSelectedEditorFocus,
             onTitleChange = viewModel::updateTitle,
             onTitleSelected = { index -> viewModel.selectItem(index) },
             onDocumentChanged = viewModel::updateDocumentBuffer,
            documentSelection = viewModel.documentSelection.collectAsStateWithLifecycle().value,
            onDocumentSelection = viewModel::selectDocumentRange,
            onListStyle = viewModel::setListStyle,
            onChecklistToggle = viewModel::toggleChecklist,
            onColor = viewModel::setTextColor,
            onSize = viewModel::setTextSize,
            onAlignment = viewModel::setTextAlignment,
            activeStyles = JournalInlineStyle.values().filterTo(mutableSetOf(), viewModel::isInlineStyleActive),
            onToggleStyle = viewModel::toggleInlineStyle,
            onMedia = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onRetryMedia = { documentMediaPicker.launch(arrayOf("image/*", "video/*")) },
            onRecord = {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.startRecording()
                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStopRecording = viewModel::stopRecording,
            onCancelRecording = viewModel::cancelRecording,
            onLocation = {
                val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.useCurrentLocation() else locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            },
            onToggleLocation = viewModel::toggleLocationEditor,
            onUpdateLocation = viewModel::updateLocation,
            onSaveLocation = viewModel::saveLocation,
            onToggleGoal = viewModel::toggleGoal,
            onToggleTodo = viewModel::toggleTodo,
            onSaveTags = viewModel::setTags,
            onRemoveMedia = viewModel::removeMediaBlock,
            onRemoveComponent = viewModel::removeComponent,
            onRemoveImage = viewModel::removeImage,
            mediaStore = mediaStore,
        )
        return
    }
    Box(modifier = modifier.fillMaxSize().navigationBarsPadding().background(DavePalette.JournalPaper)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                .then(if (journalEntryKey == null) Modifier.nestedScroll(edgeConnection) else Modifier)
                .graphicsLayer { translationY = edgePull },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
        ) {
            item(key = "journal-location") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                    val titleIndex = visibleItems.indexOfFirst { it is JournalEditorItem.Text && it.isTitle }
                    val title = visibleItems.getOrNull(titleIndex) as? JournalEditorItem.Text
                    com.fishking.core.ui.DaveJournalTitle(title?.value ?: TextFieldValue(""), title?.color,
                        title?.textSize ?: com.fishking.core.model.JournalTextSize.TITLE, title?.styleSpans.orEmpty(),
                        0L,
                        {},
                        {
                            if (titleIndex >= 0) viewModel.selectItem(titleIndex)
                            fullScreenEditor = true
                        },
                        readOnly = true,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                DaveJournalLocation(document?.entry?.takeIf { it.entryDate == selectedDate }?.locationName)
                document?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 5.dp)
                            .clickable { fullScreenEditor = true },
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
            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DavePalette.Meta)
                }
            }
            itemsIndexed(visibleItems, key = { _, item -> item.editorKey }) { index, item ->
                when (item) {
                    is JournalEditorItem.Text -> if (!item.isTitle) DaveJournalLine(
                        value = item.value,
                        textColor = item.color,
                        textSize = item.textSize,
                        styleSpans = item.styleSpans,
                        textAlignment = item.textAlignment,
                        listStyle = item.listStyle,
                        listOrdinal = journalListOrdinal(visibleItems, index),
                        isChecked = item.isChecked,
                        selected = false,
                        focusRequestToken = 0L,
                        onSelected = { viewModel.selectItem(index); fullScreenEditor = true },
                        onValueChange = {},
                        onDelete = {},
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        minHeight = 132.dp,
                        maxHeight = 300.dp,
                        readOnly = true,
                    )
                    is JournalEditorItem.Media -> when (item.type) {
                        JournalBlockType.IMAGE -> {
                            val motionVideos by androidx.compose.runtime.produceState<Map<String, String>>(emptyMap(), item.assets) {
                                val resolvedMotionVideos = buildMap {
                                    item.assets.forEach { asset ->
                                        mediaStore.motionVideoPath(asset.privatePath)?.let { put(asset.privatePath, it) }
                                    }
                                }
                                value = resolvedMotionVideos
                            }
                            DaveJournalImageRow(
                            paths = item.assets.map { it.privatePath },
                            motionVideos = motionVideos,
                            onDeleteImage = {},
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            editable = false,
                        )
                        }
                        JournalBlockType.GIF -> DaveJournalGifBlock(
                            path = item.assets.single().privatePath,
                            onDelete = {},
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            editable = false,
                        )
                        JournalBlockType.VIDEO -> DaveJournalVideoBlock(
                            path = item.assets.single().privatePath,
                            durationMillis = item.assets.single().durationMillis,
                            onDelete = {},
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            editable = false,
                        )
                        JournalBlockType.AUDIO -> DaveJournalAudioBlock(
                            path = item.assets.single().privatePath,
                            durationMillis = item.assets.single().durationMillis,
                            onDelete = {},
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            editable = false,
                        )
                        else -> Unit
                    }
                    is JournalEditorItem.Component -> JournalInlineComponent(
                        type = item.type, document = document, goals = goals, review = review,
                        onEdit = { viewModel.selectItem(index); fullScreenEditor = true },
                        onRemove = null,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            val linkedGoals = goals.filter { it.goal.id in document?.linkedGoalIds.orEmpty() }
            if (linkedGoals.isNotEmpty()) item(key = "linked-goals") {
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
                        }
                    }
                }
            }
            val linkedTodos = (review?.completedTodos.orEmpty() + review?.openTodos.orEmpty())
                .filter { it.id in document?.linkedTodoIds.orEmpty() }
            if (linkedTodos.isNotEmpty()) item(key = "linked-todos") {
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
                        }
                    }
                }
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
    }
}

private enum class JournalFullscreenPanel { NONE, FORMAT, LOCATION, LINK, TAGS }

@android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun JournalFullscreenEditor(
    items: List<JournalEditorItem>,
    selectedIndex: Int,
    focusRequest: JournalFocusRequest,
    document: com.fishking.core.model.JournalDocument?,
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    review: com.fishking.core.model.DailyReview?,
    locationEditorExpanded: Boolean,
    locationDraft: String,
    locating: Boolean,
    importingMedia: Boolean,
    mediaImportError: String?,
    isRecording: Boolean,
    recordingElapsedMillis: Long,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    tagSaveError: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRequestFocus: () -> Unit,
    onTitleChange: (TextFieldValue) -> Unit,
    onTitleSelected: (Int) -> Unit,
    onDocumentChanged: (JournalDocumentBuffer, Int, Int) -> Unit,
    documentSelection: androidx.compose.ui.text.TextRange,
    onDocumentSelection: (Int, Int) -> Unit,
    onListStyle: (com.fishking.core.model.JournalListStyle) -> Unit,
    onChecklistToggle: (String) -> Unit,
    onColor: (Long?) -> Unit,
    onSize: (com.fishking.core.model.JournalTextSize) -> Unit,
    onAlignment: (JournalTextAlignment) -> Unit,
    activeStyles: Set<JournalInlineStyle>,
    onToggleStyle: (JournalInlineStyle) -> Unit,
    onMedia: () -> Unit,
    onRetryMedia: () -> Unit,
    onRecord: () -> Unit,
    onLocation: () -> Unit,
    onToggleLocation: () -> Unit,
    onUpdateLocation: (String) -> Unit,
    onSaveLocation: () -> Unit,
    onToggleGoal: (String) -> Unit,
    onToggleTodo: (String) -> Unit,
    onSaveTags: (String) -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onRemoveComponent: (Int) -> Unit,
    onRemoveImage: (Int, Int) -> Unit,
    mediaStore: JournalMediaStore,
) {
    var panel by remember { mutableStateOf(JournalFullscreenPanel.NONE) }
    var pendingPanel by remember { mutableStateOf(JournalFullscreenPanel.NONE) }
    var tagDraft by remember(document?.entry?.id) { mutableStateOf(document?.tags.orEmpty().joinToString(" ")) }
    val tagFocusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val closePanel = { returnToText: Boolean ->
        panel = JournalFullscreenPanel.NONE
        pendingPanel = JournalFullscreenPanel.NONE
        if (returnToText) {
            onRequestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    LaunchedEffect(pendingPanel, imeBottom) {
        // Wait for the IME to finish hiding before measuring a replacement
        // panel, so it cannot momentarily sit on top of the closing keyboard.
        if (pendingPanel != JournalFullscreenPanel.NONE && imeBottom == 0) {
            panel = pendingPanel
            pendingPanel = JournalFullscreenPanel.NONE
        }
    }
    LaunchedEffect(focusRequest.token, panel, pendingPanel, isRecording) {
        if (focusRequest.token > 0 && panel == JournalFullscreenPanel.NONE &&
            pendingPanel == JournalFullscreenPanel.NONE && !isRecording) keyboard?.show()
    }
    fun openPanel(next: JournalFullscreenPanel) {
        if (panel == next) { closePanel(false); return }
        // Formatting must retain the actual TextField selection so colour,
        // size and inline styles apply to those characters. Other panels own
        // their own inputs and can release the editor focus.
        if (next != JournalFullscreenPanel.FORMAT) focusManager.clearFocus(force = false)
        keyboard?.hide()
        if (next == JournalFullscreenPanel.LOCATION && !locationEditorExpanded) onToggleLocation()
        if (next == JournalFullscreenPanel.TAGS) tagDraft = document?.tags.orEmpty().joinToString(" ")
        panel = JournalFullscreenPanel.NONE
        pendingPanel = next
    }
    LaunchedEffect(panel, imeBottom) {
        // Selection handles stay attached to the text field while the format
        // panel replaces the IME. If Android tries to reopen the keyboard
        // during handle adjustment, keep the panel stable.
        if (panel == JournalFullscreenPanel.FORMAT && imeBottom > 0) keyboard?.hide()
    }
    val selectedText = items.getOrNull(selectedIndex) as? JournalEditorItem.Text
    val titleIndex = items.indexOfFirst { it is JournalEditorItem.Text && it.isTitle }
    val title = items.getOrNull(titleIndex) as? JournalEditorItem.Text
    val bodyItems = items.filterNot { it is JournalEditorItem.Text && it.isTitle }
    val fullBuffer = journalItemsToDocumentBuffer(items)
    val firstBodyKey = bodyItems.firstOrNull()?.editorKey
    val bodyOffset = fullBuffer.ranges().firstOrNull { it.node.block.id == firstBodyKey }?.start ?: 0
    val bodySelection = androidx.compose.ui.text.TextRange(
        (documentSelection.start - bodyOffset).coerceIn(0, journalItemsToDocumentBuffer(bodyItems).length),
        (documentSelection.end - bodyOffset).coerceIn(0, journalItemsToDocumentBuffer(bodyItems).length),
    )
    val editorFocusAllowed = panel == JournalFullscreenPanel.NONE && pendingPanel == JournalFullscreenPanel.NONE && !isRecording
    LaunchedEffect(Unit) { onDocumentSelection(documentSelection.start, documentSelection.end) }
    val characterCount = items.filterIsInstance<JournalEditorItem.Text>()
        .filterNot(JournalEditorItem.Text::isTitle)
        .sumOf { item -> item.text.count { !it.isWhitespace() } }
    JournalEditorLayout(header = {
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(DavePalette.JournalPaper),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { androidx.compose.material3.Icon(Icons.Outlined.ArrowBack, "返回", tint = DavePalette.Ink) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUndo, enabled = canUndo) { androidx.compose.material3.Icon(Icons.Outlined.Undo, "撤销", tint = if (canUndo) DavePalette.Ink else DavePalette.Meta.copy(alpha = .35f)) }
                IconButton(onClick = onRedo, enabled = canRedo) { androidx.compose.material3.Icon(Icons.Outlined.Redo, "重做", tint = if (canRedo) DavePalette.Ink else DavePalette.Meta.copy(alpha = .35f)) }
                androidx.compose.material3.TextButton(onClick = onDone) { Text("完成", color = DavePalette.HeaderGreenDark, fontWeight = FontWeight.Bold) }
            }
        }, body = {
            Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            com.fishking.core.ui.DaveJournalTitle(
                value = title?.value ?: TextFieldValue(""),
                color = title?.color,
                textSize = title?.textSize ?: com.fishking.core.model.JournalTextSize.TITLE,
                styleSpans = title?.styleSpans.orEmpty(),
                focusRequestToken = if (editorFocusAllowed && selectedIndex == titleIndex) focusRequest.token else 0L,
                onValueChange = onTitleChange,
                onSelected = { if (titleIndex >= 0) onTitleSelected(titleIndex) },
                textAlignment = title?.textAlignment ?: JournalTextAlignment.LEFT,
            )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = DavePalette.Divider,
            )
            JournalSingleDocumentEditor(
                items = bodyItems,
                selection = bodySelection,
                modifier = Modifier.fillMaxWidth().weight(1f),
                requestFocusToken = if (editorFocusAllowed && selectedIndex != titleIndex) focusRequest.token else 0L,
                keyboardAllowed = editorFocusAllowed,
                onDocumentChanged = { bodyBuffer, start, end ->
                    val rebuiltBody = journalItemsFromDocumentBuffer(bodyBuffer, bodyItems)
                    val combinedItems = listOfNotNull(title) + rebuiltBody
                    val combinedBuffer = journalItemsToDocumentBuffer(combinedItems)
                    val combinedOffset = combinedBuffer.ranges().firstOrNull { it.node.block.id == rebuiltBody.firstOrNull()?.editorKey }?.start ?: 0
                    onDocumentChanged(combinedBuffer, combinedOffset + start, combinedOffset + end)
                },
                onSelectionChanged = { start, end -> onDocumentSelection(bodyOffset + start, bodyOffset + end) },
                onChecklistToggle = onChecklistToggle,
                onTextInteraction = { panel = JournalFullscreenPanel.NONE; pendingPanel = JournalFullscreenPanel.NONE },
                attachment = { key ->
                    val index = items.indexOfFirst { it.editorKey == key }
                    when (val item = items.getOrNull(index)) {
                        is JournalEditorItem.Media -> when (item.type) {
                            JournalBlockType.IMAGE -> {
                                val motionVideos by androidx.compose.runtime.produceState<Map<String, String>>(emptyMap(), item.assets) {
                                    val resolvedMotionVideos = buildMap {
                                        item.assets.forEach { asset ->
                                            mediaStore.motionVideoPath(asset.privatePath)?.let { put(asset.privatePath, it) }
                                        }
                                    }
                                    value = resolvedMotionVideos
                                }
                                DaveJournalImageRow(item.assets.map { it.privatePath }, { onRemoveImage(index, it) },
                                    Modifier.fillMaxWidth(), motionVideos = motionVideos)
                            }
                            JournalBlockType.GIF -> item.assets.firstOrNull()?.let { DaveJournalGifBlock(it.privatePath, { onRemoveMedia(index) }, Modifier.fillMaxWidth()) }
                            JournalBlockType.VIDEO -> item.assets.firstOrNull()?.let { DaveJournalVideoBlock(it.privatePath, it.durationMillis, { onRemoveMedia(index) }, Modifier.fillMaxWidth()) }
                            JournalBlockType.AUDIO -> item.assets.firstOrNull()?.let { DaveJournalAudioBlock(it.privatePath, it.durationMillis, { onRemoveMedia(index) }, Modifier.fillMaxWidth()) }
                            else -> Unit
                        }
                        is JournalEditorItem.Component -> JournalInlineComponent(item.type, document, goals, review,
                            onEdit = {
                                openPanel(when (item.type) {
                                    JournalBlockType.LOCATION -> JournalFullscreenPanel.LOCATION
                                    JournalBlockType.TAGS -> JournalFullscreenPanel.TAGS
                                    else -> JournalFullscreenPanel.LINK
                                })
                            }, onRemove = { onRemoveComponent(index) })
                        else -> Unit
                    }
                },
            )
            }
        }, footer = {
        Column(
            Modifier.fillMaxWidth().background(DavePalette.JournalPaper),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            when (panel) {
                JournalFullscreenPanel.FORMAT -> Column {
                JournalFormattingPanel(
                    selectedColor = selectedText?.color,
                    selectedSize = selectedText?.textSize,
                    selectedAlignment = selectedText?.textAlignment ?: JournalTextAlignment.LEFT,
                    selectedListStyle = selectedText?.listStyle ?: com.fishking.core.model.JournalListStyle.NONE,
                    activeStyles = activeStyles,
                    onColor = onColor,
                    onSize = onSize,
                    onAlignment = onAlignment,
                    onListStyle = onListStyle,
                    onToggleStyle = onToggleStyle,
                )
                }
                JournalFullscreenPanel.LOCATION -> if (locationEditorExpanded) DaveLocationEditor(locationDraft, onUpdateLocation, onLocation, locating, { onSaveLocation(); closePanel(false) }, Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                JournalFullscreenPanel.LINK -> JournalLinkPicker(goals, review, document?.linkedGoalIds.orEmpty().toSet(), document?.linkedTodoIds.orEmpty().toSet(), onToggleGoal, onToggleTodo, { closePanel(false) }, Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                JournalFullscreenPanel.TAGS -> {
                    JournalTagsPanel(
                        value = tagDraft,
                        onValueChange = { tagDraft = it.replace('\n', ' ') },
                        onApply = { onSaveTags(tagDraft); closePanel(false) },
                        focusRequester = tagFocusRequester,
                    )
                }
                JournalFullscreenPanel.NONE -> Unit
            }
            }
            tagSaveError?.let { Text(it, color = DavePalette.Urgent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp), fontSize = 12.sp) }
             mediaImportError?.let { error ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(error, color = DavePalette.Urgent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onRetryMedia) {
                        Text("文件选择器", color = DavePalette.HeaderGreenDark, fontSize = 12.sp)
                    }
                }
            }
            if (isRecording) {
                DaveJournalRecordingBar(recordingElapsedMillis, onStopRecording, onCancelRecording)
            }
            Row(
                Modifier.fillMaxWidth().background(DavePalette.JournalPaper).padding(horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FullscreenTool(Icons.Outlined.FormatPaint, "文字格式", { openPanel(JournalFullscreenPanel.FORMAT) })
                FullscreenTool(Icons.Outlined.AttachFile, "图片/视频", {
                    focusManager.clearFocus(); keyboard?.hide()
                    panel = JournalFullscreenPanel.NONE; pendingPanel = JournalFullscreenPanel.NONE; onMedia()
                }, enabled = !importingMedia)
                FullscreenTool(Icons.Outlined.MicNone, "录音", {
                    focusManager.clearFocus(); keyboard?.hide()
                    panel = JournalFullscreenPanel.NONE; pendingPanel = JournalFullscreenPanel.NONE; onRecord()
                }, enabled = !isRecording, active = isRecording)
                FullscreenTool(Icons.Outlined.LocationOn, "地点", { openPanel(JournalFullscreenPanel.LOCATION) })
                FullscreenTool(Icons.Outlined.Link, "关联", { openPanel(JournalFullscreenPanel.LINK) })
                FullscreenTool(Icons.Outlined.Tag, "TAG", { openPanel(JournalFullscreenPanel.TAGS) }, active = document?.tags?.isNotEmpty() == true)
            }
        }
    })
}

@Composable
internal fun JournalTagsPanel(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val ownedFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: ownedFocusRequester
    LaunchedEffect(actualFocusRequester) {
        // The document body is an Android EditText while TAG is a Compose
        // TextField. Transfer focus only after this panel has been attached,
        // otherwise the native editor can remain the IME owner.
        actualFocusRequester.requestFocus()
        keyboard?.show()
    }
    Surface(
        color = DavePalette.JournalPaper,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.replace('\n', ' ')) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(actualFocusRequester)
                    .semantics { contentDescription = "日记TAG输入" }
                    .background(DavePalette.JournalPaper, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                placeholder = { Text("例如：生活 旅行", color = DavePalette.Meta.copy(alpha = .6f)) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DavePalette.JournalPaper,
                    unfocusedContainerColor = DavePalette.JournalPaper,
                    disabledContainerColor = DavePalette.JournalPaper,
                ),
            )
            androidx.compose.material3.TextButton(onClick = onApply) {
                Text("应用", color = DavePalette.HeaderGreenDark)
            }
        }
    }
}

@Composable
private fun JournalInlineComponent(
    type: JournalBlockType,
    document: com.fishking.core.model.JournalDocument?,
    goals: List<com.fishking.core.model.LifeGoalWithEvents>,
    review: com.fishking.core.model.DailyReview?,
    onEdit: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val icon = when (type) {
        JournalBlockType.LOCATION -> Icons.Outlined.LocationOn
        JournalBlockType.TAGS -> Icons.Outlined.Tag
        else -> Icons.Outlined.Link
    }
    val label = when (type) {
        JournalBlockType.LOCATION -> document?.entry?.locationName?.takeIf(String::isNotBlank) ?: "地点 · 点此编辑"
        JournalBlockType.TAGS -> document?.tags.orEmpty().joinToString(" ") { "#$it" }.ifBlank { "TAG · 点此编辑" }
        else -> {
            val goalLabels = document?.linkedGoalIds.orEmpty().map { id -> goals.firstOrNull { it.goal.id == id }?.goal?.title ?: "已关联人生目标" }
            val todos = review?.completedTodos.orEmpty() + review?.openTodos.orEmpty()
            val todoLabels = document?.linkedTodoIds.orEmpty().map { id -> todos.firstOrNull { it.id == id }?.title ?: "已关联待办" }
            (goalLabels + todoLabels).joinToString(" · ").ifBlank { "关联内容 · 点此编辑" }
        }
    }
    DaveSwipeDeleteContainer(
        onDelete = { onRemove?.invoke() },
        onEdit = onEdit,
        modifier = modifier,
        enabled = onRemove != null,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = DavePalette.HeaderGreenDark, modifier = Modifier.size(24.dp))
            Text(label, color = DavePalette.Ink, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
        }
    }
}

@Composable
private fun JournalFormattingPanel(
    selectedColor: Long?,
    selectedSize: com.fishking.core.model.JournalTextSize?,
    selectedAlignment: JournalTextAlignment,
    selectedListStyle: com.fishking.core.model.JournalListStyle,
    activeStyles: Set<JournalInlineStyle>,
    onColor: (Long?) -> Unit,
    onSize: (com.fishking.core.model.JournalTextSize) -> Unit,
    onAlignment: (JournalTextAlignment) -> Unit,
    onListStyle: (com.fishking.core.model.JournalListStyle) -> Unit,
    onToggleStyle: (JournalInlineStyle) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(DavePalette.JournalPaper).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
    ) {
        DaveJournalFormatStrip(selectedColor, selectedSize, onColor, onSize)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatToggle("B", "粗体", JournalInlineStyle.BOLD in activeStyles) { onToggleStyle(JournalInlineStyle.BOLD) }
            FormatToggle("I", "斜体", JournalInlineStyle.ITALIC in activeStyles, italic = true) { onToggleStyle(JournalInlineStyle.ITALIC) }
            FormatToggle("U̲", "下划线", JournalInlineStyle.UNDERLINE in activeStyles) { onToggleStyle(JournalInlineStyle.UNDERLINE) }
            FormatToggle("S̶", "删除线", JournalInlineStyle.STRIKETHROUGH in activeStyles) { onToggleStyle(JournalInlineStyle.STRIKETHROUGH) }
            FormatToggle("▰", "荧光笔", JournalInlineStyle.HIGHLIGHT in activeStyles, highlight = true) { onToggleStyle(JournalInlineStyle.HIGHLIGHT) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParagraphToggle(Icons.Outlined.Checklist, "勾选清单", selectedListStyle == com.fishking.core.model.JournalListStyle.CHECKLIST) {
                onListStyle(
                    if (selectedListStyle == com.fishking.core.model.JournalListStyle.CHECKLIST) {
                        com.fishking.core.model.JournalListStyle.NONE
                    } else {
                        com.fishking.core.model.JournalListStyle.CHECKLIST
                    },
                )
            }
            ParagraphToggle(Icons.Outlined.FormatAlignLeft, "左对齐", selectedAlignment == JournalTextAlignment.LEFT) {
                onAlignment(JournalTextAlignment.LEFT)
            }
            ParagraphToggle(Icons.Outlined.FormatAlignCenter, "居中对齐", selectedAlignment == JournalTextAlignment.CENTER) {
                onAlignment(JournalTextAlignment.CENTER)
            }
            ParagraphToggle(Icons.Outlined.FormatAlignRight, "右对齐", selectedAlignment == JournalTextAlignment.RIGHT) {
                onAlignment(JournalTextAlignment.RIGHT)
            }
        }
    }
}

@Composable
private fun ParagraphToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            .background(if (active) DavePalette.HeaderGreen.copy(alpha = .14f) else Color.Transparent)
            .semantics { contentDescription = description },
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = if (active) DavePalette.HeaderGreenDark else DavePalette.Ink,
            modifier = Modifier.size(27.dp),
        )
    }
}

@Composable
private fun JournalCanvasMeta(
    document: com.fishking.core.model.JournalDocument?,
    characterCount: Int,
) {
    val entry = document?.entry
    val dateLabel = entry?.entryDate?.let { "${it.year}/${it.monthValue}/${it.dayOfMonth}" }.orEmpty()
    val timeLabel = entry?.entryTime?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty()
    val tagsLabel = document?.tags?.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" }.orEmpty()
    Text(
        listOf(dateLabel, timeLabel, "${characterCount}字", tagsLabel)
            .filter(String::isNotBlank)
            .joinToString("  |  "),
        color = DavePalette.Meta.copy(alpha = .72f),
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun FormatToggle(
    label: String,
    description: String,
    active: Boolean,
    italic: Boolean = false,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            .background(if (active) DavePalette.HeaderGreen.copy(alpha = .14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = DavePalette.Ink,
            fontSize = if (highlight) 22.sp else 18.sp,
            fontWeight = if (description == "粗体") FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            modifier = if (highlight) Modifier.background(Color(0x66FFE066)).padding(horizontal = 3.dp) else Modifier,
        )
    }
}

@Composable
private fun FullscreenTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        androidx.compose.material3.Icon(icon, null, modifier = Modifier.size(32.dp), tint = if (active) DavePalette.HeaderGreenDark else if (enabled) DavePalette.Ink else DavePalette.Meta.copy(alpha = .35f))
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
        modifier.fillMaxWidth().background(DavePalette.JournalPaper, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp),
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
