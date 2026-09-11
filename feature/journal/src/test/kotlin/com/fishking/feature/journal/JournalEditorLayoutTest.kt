package com.fishking.feature.journal

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
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
class JournalEditorLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null

    @After fun closeActivity() {
        val controller = activity
        compose.runOnUiThread { controller?.get()?.setContent { } }
        compose.waitForIdle()
        compose.runOnUiThread { controller?.pause()?.stop()?.destroy() }
    }

    @Test fun toolbarFollowsOneBottomInsetThroughImeAnimationAndPanelReplacement() {
        val imeDp = mutableIntStateOf(0)
        val panelDp = mutableIntStateOf(0)
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent {
                val density = LocalDensity.current
                Box(Modifier.size(width = 300.dp, height = 420.dp).testTag("viewport")) {
                    JournalEditorLayout(
                        imeInsets = WindowInsets(bottom = with(density) { imeDp.intValue.dp.roundToPx() }),
                        navigationInsets = WindowInsets(bottom = with(density) { 24.dp.roundToPx() }),
                        header = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("header")) },
                        body = { Box(Modifier.fillMaxSize().testTag("body")) },
                        footer = {
                            Column(Modifier.fillMaxWidth().testTag("footer")) {
                                Box(Modifier.fillMaxWidth().height(panelDp.intValue.dp))
                                Box(Modifier.fillMaxWidth().height(48.dp).testTag("toolbar"))
                            }
                        },
                    )
                }
            }
        }
        for (ime in listOf(0, 48, 120, 180, 240, 180, 120, 48, 0, 180, 0)) {
            compose.runOnIdle {
                imeDp.intValue = ime
                panelDp.intValue = if (ime == 0) 90 else 0
            }
            val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
            val header = compose.onNodeWithTag("header").getUnclippedBoundsInRoot()
            val body = compose.onNodeWithTag("body").getUnclippedBoundsInRoot()
            val footer = compose.onNodeWithTag("footer").getUnclippedBoundsInRoot()
            val toolbar = compose.onNodeWithTag("toolbar").getUnclippedBoundsInRoot()
            assertEquals(viewport.top, header.top)
            assertEquals(header.bottom, body.top)
            assertEquals(body.bottom, footer.top)
            assertEquals(viewport.bottom - maxOf(ime, 24).dp, toolbar.bottom)
        }
    }

    @Test fun tagPanelTakesInputFocusWhenItAppears() {
        val value = mutableStateOf("")
        compose.runOnUiThread {
            activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            activity!!.get().setContent {
                JournalTagsPanel(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onApply = {},
                )
            }
        }

        compose.onNodeWithContentDescription("日记TAG输入")
            .assertIsFocused()
            .performTextInput("生活")
        compose.runOnIdle { assertEquals("生活", value.value) }
    }
}
