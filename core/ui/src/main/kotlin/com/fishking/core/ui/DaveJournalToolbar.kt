package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishking.core.model.JournalTextSize

@Composable
fun DaveJournalFormatStrip(
    selectedColor: Long?,
    selectedSize: JournalTextSize?,
    onColor: (Long?) -> Unit,
    onSize: (JournalTextSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(DavePalette.JournalPaper).padding(horizontal = 8.dp, vertical = 3.dp)) {
        DaveMacaronPalette(
            selected = selectedColor,
            onSelected = onColor,
            includeDefaultInk = true,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(JournalTextSize.SMALL, JournalTextSize.BODY, JournalTextSize.LARGE, JournalTextSize.TITLE).forEach { size ->
                Text(
                    "A",
                    color = DavePalette.Ink,
                    fontSize = when (size) { JournalTextSize.SMALL -> 13.sp; JournalTextSize.BODY -> 16.sp; JournalTextSize.LARGE -> 20.sp; JournalTextSize.TITLE -> 24.sp },
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (selectedSize == size) DavePalette.HeaderGreen.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .then(if (selectedSize == size) Modifier.border(1.dp, DavePalette.Meta, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable { onSize(size) }.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}
