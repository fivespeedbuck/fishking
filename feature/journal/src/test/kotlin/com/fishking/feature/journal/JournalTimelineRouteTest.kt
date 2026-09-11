package com.fishking.feature.journal

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.fishking.core.model.JournalBlockDraft
import com.fishking.core.model.JournalDocument
import com.fishking.core.model.JournalMediaAsset
import com.fishking.core.model.JournalTimelineItem
import com.fishking.core.usecase.JournalRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JournalTimelineRouteTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    private val date = LocalDate.of(2026, 9, 11)

    @After fun closeActivity() {
        val controller = activity
        compose.runOnUiThread { controller?.get()?.setContent { } }
        compose.waitForIdle()
        compose.runOnUiThread { controller?.pause()?.stop()?.destroy() }
    }

    private fun show(repository: TimelineRepository) {
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    JournalTimelineRoute(repository, date, onDateChange = {}) { _, day, _, _ ->
                        Text("编辑 $day")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun firstCompositionOfEmptyTimelineCanOpenNewEntry() {
        show(TimelineRepository(emptyList()))
        compose.onNodeWithText("这个月还没有日记，点击 + 记录今天").assertIsDisplayed()
        compose.onNodeWithContentDescription("新建").performClick()
        compose.onNodeWithText("编辑 $date").assertIsDisplayed()
    }

    @Test fun firstCompositionOfExistingEntryIncludesTextMediaAndMetadata() {
        show(TimelineRepository(listOf(entry())))
        compose.onNodeWithText("已有日记").assertIsDisplayed()
        compose.onNodeWithText("正文还在").assertIsDisplayed()
        compose.onNodeWithText("#生活  #旅行").assertIsDisplayed()
        compose.onNodeWithText("附件 1").assertIsDisplayed()
        compose.onNodeWithText("已有日记").performClick()
        compose.onNodeWithText("编辑 $date").assertIsDisplayed()
    }

    @Test fun failedInitialTimelineCollectionShowsRetryAndDoesNotPretendRecordsAreEmpty() {
        val repository = TimelineRepository(listOf(entry())).apply { failCollection = true }
        show(repository)
        compose.onNodeWithText("日记读取失败，原记录未改动").assertIsDisplayed()
        compose.onNodeWithText("这个月还没有日记，点击 + 记录今天").assertDoesNotExist()
        repository.failCollection = false
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("已有日记").assertIsDisplayed()
        assertEquals(2, repository.collectionCount)
    }

    private fun entry() = JournalTimelineItem(
        id = "existing", date = date, time = LocalTime.of(9, 10), title = "已有日记",
        excerpt = "正文还在", mediaCount = 1, location = "宁波",
        thumbnailPaths = listOf("/missing/private-photo.jpg"), tags = listOf("生活", "旅行"),
    )
}

private class TimelineRepository(private val entries: List<JournalTimelineItem>) : JournalRepository {
    var failCollection = false
    var collectionCount = 0
    override fun observeTimelineRange(from: LocalDate, through: LocalDate) = flow {
        collectionCount++
        check(!failCollection) { "Timeline read failed" }
        emit(entries.filter { it.date in from..through })
    }
    override fun observeDocument(date: LocalDate) = flowOf<JournalDocument?>(null)
    override suspend fun saveBlocks(date: LocalDate, blocks: List<JournalBlockDraft>): List<String> = error("Unexpected write")
    override suspend fun setLocation(date: LocalDate, locationName: String?, latitude: Double?, longitude: Double?) = error("Unexpected write")
    override suspend fun registerMedia(privatePath: String, previewPath: String?, mimeType: String, sizeBytes: Long, checksum: String?, durationMillis: Long?): JournalMediaAsset = error("Unexpected write")
    override suspend fun abandonUnlinkedMedia(assetId: String) = false
    override suspend fun linkGoal(date: LocalDate, goalId: String) = false
    override suspend fun unlinkGoal(date: LocalDate, goalId: String) = Unit
    override suspend fun pendingMediaCleanup(): List<JournalMediaAsset> = emptyList()
    override suspend fun confirmMediaFileDeleted(assetId: String) = false
}
