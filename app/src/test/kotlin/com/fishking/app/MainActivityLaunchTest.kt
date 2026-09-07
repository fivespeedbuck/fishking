package com.fishking.app

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

    private fun assertFalse(value: Boolean) {
        assertTrue(!value)
    }
}
