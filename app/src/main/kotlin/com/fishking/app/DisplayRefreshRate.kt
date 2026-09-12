package com.fishking.app

import android.app.Activity
import android.view.Display

internal const val SETTINGS_PREFERENCES_NAME = "fishking_settings"
internal const val HIGH_REFRESH_RATE_KEY = "high_refresh_rate"

internal fun highestRefreshRateRequest(enabled: Boolean, supportedRates: FloatArray): Float {
    if (!enabled) return 0f
    return supportedRates.asSequence()
        .filter { it.isFinite() && it > 0f }
        .maxOrNull()
        ?: 0f
}

internal fun Display.maximumSupportedRefreshRate(): Float {
    val currentMode = mode
    val ratesAtCurrentResolution = supportedModes.asSequence()
        .filter { candidate ->
            candidate.physicalWidth == currentMode.physicalWidth &&
                candidate.physicalHeight == currentMode.physicalHeight
        }
        .map { it.refreshRate }
        .toList()
        .toFloatArray()
    return highestRefreshRateRequest(true, ratesAtCurrentResolution + refreshRate)
}

internal fun Activity.maximumSupportedRefreshRate(): Float {
    @Suppress("DEPRECATION")
    val targetDisplay = window.decorView.display ?: windowManager.defaultDisplay
    return targetDisplay.maximumSupportedRefreshRate()
}

/**
 * Requests a refresh rate for this window. Android and the device firmware retain the final
 * decision, so callers must describe the result as a request rather than a measured rate.
 */
internal fun Activity.applyHighRefreshRatePreference(enabled: Boolean): Float {
    val requestedRate = if (enabled) maximumSupportedRefreshRate() else 0f
    val layoutParams = window.attributes
    layoutParams.preferredRefreshRate = requestedRate
    window.attributes = layoutParams
    return requestedRate
}
