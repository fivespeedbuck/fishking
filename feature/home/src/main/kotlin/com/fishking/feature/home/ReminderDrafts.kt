package com.fishking.feature.home

import com.fishking.core.model.TodoReminderSpec
import java.time.LocalTime

internal object ReminderDrafts {
    fun decode(value: String): List<TodoReminderSpec> = value.split(',').filter(String::isNotBlank).mapIndexed { index, token ->
        val pieces = token.split('|')
        if (pieces.size == 1) TodoReminderSpec(localTime = LocalTime.parse(token), position = index.toLong())
        else TodoReminderSpec(pieces[0].toInt(), LocalTime.parse(pieces[1]), index.toLong(), pieces.getOrNull(2) != "false")
    }
    fun encode(values: List<TodoReminderSpec>): String = values.joinToString(",") {
        if (it.dayOffset == 0 && it.isEnabled) it.localTime.toString() else "${it.dayOffset}|${it.localTime}|${it.isEnabled}"
    }
    fun replace(value: String, previous: LocalTime?, replacement: LocalTime?, dayOffset: Int? = null): String {
        val values = decode(value).toMutableList()
        val index = values.indexOfFirst { it.localTime == previous }
        val prior = if (index >= 0) values.removeAt(index) else null
        if (replacement != null) values.add((prior ?: TodoReminderSpec(localTime = replacement)).copy(
            localTime = replacement, dayOffset = dayOffset ?: prior?.dayOffset ?: 0))
        return encode(values.distinctBy { Triple(it.dayOffset, it.localTime, it.isEnabled) }.sortedWith(compareBy({ it.dayOffset }, { it.localTime })))
    }
}
