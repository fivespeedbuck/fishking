package com.fishking.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fishking.core.ui.DavePalette

/**
 * One bottom-inset owner for the entire editor. IME and navigation overlap;
 * their union is consumed once, not added or applied to a scrolling child.
 * The Activity must use adjustResize: unspecified mode lets Android pan the
 * entire already-padded Compose tree to a focused text field.
 */
@Composable
internal fun JournalEditorLayout(
    modifier: Modifier = Modifier,
    imeInsets: WindowInsets = WindowInsets.ime,
    navigationInsets: WindowInsets = WindowInsets.navigationBars,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(DavePalette.JournalPaper)
            .windowInsetsPadding(imeInsets.union(navigationInsets).only(WindowInsetsSides.Bottom)),
    ) {
        header()
        Box(Modifier.fillMaxWidth().weight(1f)) { body() }
        footer()
    }
}
