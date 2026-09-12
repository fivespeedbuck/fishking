package com.fishking.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRefreshRateTest {
    @Test
    fun disabledModeReturnsControlToTheSystem() {
        assertEquals(0f, highestRefreshRateRequest(false, floatArrayOf(60f, 120f, 144f)), 0f)
    }

    @Test
    fun enabledModeRequestsHighestValidDeviceRate() {
        val result = highestRefreshRateRequest(
            enabled = true,
            supportedRates = floatArrayOf(Float.NaN, -1f, 60f, 143.94f, 120f),
        )

        assertEquals(143.94f, result, 0f)
    }

    @Test
    fun enabledModeFallsBackToSystemWhenNoValidRateExists() {
        assertEquals(0f, highestRefreshRateRequest(true, floatArrayOf()), 0f)
    }
}
