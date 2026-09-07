package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth

/** Same date contract for home and journal; picking a future date is intentionally allowed. */
@Composable
fun DaveCalendar(selected: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit, entryDates: Set<LocalDate> = emptySet(), markDescription: String = "有日记") {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val today = LocalDate.now()
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(DavePalette.Card, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", fontSize = 30.sp, color = DavePalette.HeaderGreenDark, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { month = month.minusMonths(1) }.padding(10.dp))
                Text("${month.year}年 ${month.monthValue}月", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DavePalette.Ink)
                Text("›", fontSize = 30.sp, color = DavePalette.HeaderGreenDark, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { month = month.plusMonths(1) }.padding(10.dp))
            }
            Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), color = DavePalette.Meta, textAlign = TextAlign.Center) } }
            val first = month.atDay(1).dayOfWeek.value - 1
            repeat((first + month.lengthOfMonth() + 6) / 7) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(7) { day ->
                        val number = week * 7 + day - first + 1
                        val date = if (number in 1..month.lengthOfMonth()) month.atDay(number) else null
                        Box(Modifier.weight(1f).aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (date == selected) DavePalette.HeaderGreen else Color.Transparent, RoundedCornerShape(8.dp))
                            .then(if (date == today) Modifier.border(1.dp, DavePalette.HeaderGreen, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(enabled = date != null) { date?.let(onSelect) }, contentAlignment = Alignment.Center) {
                            if (date != null) Text(number.toString(), color = if (date == selected) Color.White else DavePalette.Ink, fontSize = 16.sp)
                            if (date in entryDates) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                .size(4.dp).background(if (date == selected) Color.White else DavePalette.HeaderGreen, androidx.compose.foundation.shape.CircleShape)
                                .semantics { contentDescription = "${date} $markDescription" })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("回到今天", color = DavePalette.HeaderGreenDark, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(today) }.padding(10.dp))
                Text("取消", color = DavePalette.Meta, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(10.dp))
            }
        }
    }
}
