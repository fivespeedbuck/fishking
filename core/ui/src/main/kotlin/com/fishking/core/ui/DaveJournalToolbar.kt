package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.JournalTextSize

private val journalPastels = listOf<Long?>(null, 0xFF8FA7E4, 0xFFB5D0A8, 0xFFED8FAE, 0xFFF3CB6C)

@Composable
fun DaveJournalFormatStrip(
    selectedColor: Long?,
    selectedSize: JournalTextSize?,
    onColor: (Long?) -> Unit,
    onSize: (JournalTextSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(DavePalette.JournalPaper).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        journalPastels.forEach { argb ->
            val color = argb?.let(::Color) ?: DavePalette.Ink
            Box(
                Modifier.size(if (selectedColor == argb) 27.dp else 23.dp).clip(CircleShape).background(color, CircleShape)
                    .clickable { onColor(argb) }.semantics { contentDescription = "日记文字颜色" },
            )
        }
        Spacer(Modifier.weight(1f))
        listOf(JournalTextSize.SMALL, JournalTextSize.BODY, JournalTextSize.LARGE, JournalTextSize.TITLE).forEach { size ->
            Text(
                "A",
                color = DavePalette.Ink,
                fontSize = when (size) { JournalTextSize.SMALL -> 13.sp; JournalTextSize.BODY -> 16.sp; JournalTextSize.LARGE -> 20.sp; JournalTextSize.TITLE -> 24.sp },
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (selectedSize == size) Color.White else DavePalette.JournalPaper, RoundedCornerShape(6.dp))
                    .then(if (selectedSize == size) Modifier.border(1.dp, DavePalette.Meta, RoundedCornerShape(6.dp)) else Modifier)
                    .clickable { onSize(size) }.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun DaveJournalActionBar(
    onMedia: () -> Unit,
    onRecord: () -> Unit,
    onLocation: () -> Unit,
    onLink: () -> Unit,
    onTags: () -> Unit = {},
    importingMedia: Boolean,
    hasTags: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, DavePalette.Divider, RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tools = listOf(
            Triple(Icons.Outlined.AttachFile, "图片/视频", if (importingMedia) ({}) else onMedia),
            Triple(Icons.Outlined.MicNone, "录音", onRecord),
            Triple(Icons.Outlined.LocationOn, "地点", onLocation),
            Triple(Icons.Outlined.Link, "关联", onLink),
            Triple(Icons.Outlined.Tag, "编辑日记TAG", onTags),
        )
        tools.forEachIndexed { index, (icon, label, action) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable(onClick = action)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (label == "编辑日记TAG" && hasTags) DavePalette.HeaderGreenDark else DavePalette.Ink,
                    modifier = Modifier.size(25.dp),
                )
            }
            if (index != tools.lastIndex) {
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = DavePalette.Divider,
                )
            }
        }
    }
}
