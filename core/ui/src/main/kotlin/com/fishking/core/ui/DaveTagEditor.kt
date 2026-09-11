package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared TAG panel for todo and habit editors. */
@Composable
internal fun DaveTagEditorPanel(
    title: String,
    onTitleChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(title) { mutableStateOf(daveTaskTags(title).joinToString(" ")) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier.fillMaxWidth().background(DavePalette.Card, RoundedCornerShape(9.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("自定义 TAG · 多个标签用空格分隔", color = DavePalette.Meta, fontSize = 12.sp)
        val presets = LocalPresetTags.current
        if (presets.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            presets.forEach { tag ->
                TextButton(onClick = {
                    val selected = draft.split(Regex("[\\s#,，]+" )).filter(String::isNotBlank).toMutableSet()
                    if (!selected.add(tag)) selected.remove(tag)
                    draft = selected.joinToString(" ")
                }) { Text("#$tag", color = DavePalette.Meta) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.replace('\n', ' ') },
                modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = 12.dp)
                    .semantics { contentDescription = "输入自定义TAG" },
                singleLine = true,
                textStyle = TextStyle(color = DavePalette.Ink, fontSize = 16.sp),
                cursorBrush = SolidColor(DavePalette.HeaderGreen),
                decorationBox = { input ->
                    Box {
                        if (draft.isEmpty()) Text(
                            "例如：工作 生活",
                            color = DavePalette.Meta.copy(alpha = .56f),
                            fontSize = 12.sp,
                        )
                        input()
                    }
                },
            )
            TextButton(onClick = {
                onTitleChange(replaceDaveTaskTags(title, draft))
                focusManager.clearFocus()
                keyboard?.hide()
                onDone()
            }) { Text("应用", color = DavePalette.HeaderGreenDark) }
        }
        Text("类型 TAG 自动显示；应用后点卡片 ✓ 保存", color = DavePalette.Meta, fontSize = 11.sp)
    }
}
