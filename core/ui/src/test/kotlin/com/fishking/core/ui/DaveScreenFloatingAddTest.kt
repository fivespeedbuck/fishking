package com.fishking.core.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Runtime layout checks; compilation alone is not a visible/touchable-button pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DaveScreenFloatingAddTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null

    @After fun closeActivity() {
        val controller = activity
        compose.runOnUiThread { controller?.get()?.setContent { } }
        compose.waitForIdle()
        compose.runOnUiThread { controller?.pause()?.stop()?.destroy() }
    }

    @Test fun buttonKeepsLifeGeometryAndReceivesTouchAboveThePagesHighLayer() {
        var created = 0
        var pageTouches = 0
        show(enabled = mutableStateOf(true), create = { created++ }, pageTouch = { pageTouches++ })
        val button = compose.onNodeWithContentDescription("新建").assertIsDisplayed()
        val bounds = button.getUnclippedBoundsInRoot()
        val frame = compose.onNodeWithTag("frame").getUnclippedBoundsInRoot()
        assertEquals(70.dp, bounds.right - bounds.left)
        assertEquals(70.dp, bounds.bottom - bounds.top)
        assertEquals(22.dp, frame.right - bounds.right)
        assertEquals(22.dp, frame.bottom - bounds.bottom)
        // Real pointer hit testing, not performClick's direct semantics callback.
        button.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, created); assertEquals(0, pageTouches) }
    }

    @Test fun anOpenDraftDisablesTheButtonButDoesNotRemoveIt() {
        val enabled = mutableStateOf(true)
        show(enabled, {}, {})
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("新建").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun inlineAddIsAFlowItemWithAStableTouchHeight() {
        var created = 0
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent {
                Box(Modifier.size(300.dp, 180.dp)) {
                    DaveListInlineAddAction("新建列表项", true, { created++ }, Modifier.testTag("inline-add"))
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("inline-add").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(300.dp, bounds.right - bounds.left)
        assertEquals(64.dp, bounds.bottom - bounds.top)
        compose.onNodeWithContentDescription("新建列表项").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, created) }
    }

    private fun show(enabled: androidx.compose.runtime.State<Boolean>, create: () -> Unit, pageTouch: () -> Unit) {
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent {
                Box(Modifier.size(360.dp, 640.dp)) {
                    DavePageFrame(FishKingSection.HOME, {}, header = {}, modifier = Modifier.testTag("frame")) {
                        Box(Modifier.fillMaxSize().zIndex(1000f).background(Color.Red).clickable(onClick = pageTouch))
                        DaveScreenFloatingAddAction(FishKingSection.HOME, enabled.value, create)
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
