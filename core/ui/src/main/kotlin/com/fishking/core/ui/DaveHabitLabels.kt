package com.fishking.core.ui

import com.fishking.core.model.HabitPeriod

internal fun habitPeriodTag(period: HabitPeriod, intervalDays: Int = 1): String = when (period) {
    HabitPeriod.DAILY -> "日常"
    HabitPeriod.WEEKLY -> "周常"
    HabitPeriod.MONTHLY -> "月常"
    HabitPeriod.EVERY_N_DAYS -> "每${intervalDays}天"
    HabitPeriod.AFTER_COMPLETION_N_DAYS -> "间隔${intervalDays}天"
}

internal fun habitPeriodProgress(count: Int, target: Int, period: HabitPeriod): String =
    "$count/$target" + when (period) {
        HabitPeriod.DAILY -> ""
        HabitPeriod.WEEKLY -> "·周"
        HabitPeriod.MONTHLY -> "·月"
        HabitPeriod.EVERY_N_DAYS,
        HabitPeriod.AFTER_COMPLETION_N_DAYS,
        -> ""
    }
