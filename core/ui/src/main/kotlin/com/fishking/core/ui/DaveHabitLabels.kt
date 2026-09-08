package com.fishking.core.ui

import com.fishking.core.model.HabitPeriod

internal fun habitPeriodTag(period: HabitPeriod): String = when (period) {
    HabitPeriod.DAILY -> "日常"
    HabitPeriod.WEEKLY -> "周常"
    HabitPeriod.MONTHLY -> "月常"
}

internal fun habitPeriodProgress(count: Int, target: Int, period: HabitPeriod): String =
    "$count/$target" + when (period) {
        HabitPeriod.DAILY -> ""
        HabitPeriod.WEEKLY -> "·周"
        HabitPeriod.MONTHLY -> "·月"
    }
