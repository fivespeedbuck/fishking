package com.fishking.core.location

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalLocationProviderTest {
    @Test
    fun readableLabelPrefersCityAndDistrictWithoutDuplicates() {
        assertEquals(
            "广州市 · 天河区",
            formatLocationLabel("广东省", "广州市", "天河区", "天河区", 23.0, 113.0),
        )
    }

    @Test
    fun coordinatesRemainAsOfflineFallback() {
        assertEquals(
            "23.12346, 113.98765",
            formatLocationLabel(null, null, null, null, 23.123456, 113.987654),
        )
    }
}
