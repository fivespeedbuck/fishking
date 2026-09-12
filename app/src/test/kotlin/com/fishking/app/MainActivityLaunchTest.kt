package com.fishking.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityLaunchTest {
    @Test
    fun applicationAndComposeActivityReachResumedState() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity)
        assertTrue(activity.application is FishKingApplication)
        assertFalse(activity.isFinishing)

        controller.pause().stop().destroy()
    }

    @Test
    fun savedHighRefreshPreferenceIsAppliedToTheWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(SETTINGS_PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putBoolean(HIGH_REFRESH_RATE_KEY, true).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()

        assertTrue(controller.get().window.attributes.preferredRefreshRate > 0f)
        controller.pause().stop().destroy()
        preferences.edit().remove(HIGH_REFRESH_RATE_KEY).commit()
    }

    private fun assertFalse(value: Boolean) {
        assertTrue(!value)
    }
}
