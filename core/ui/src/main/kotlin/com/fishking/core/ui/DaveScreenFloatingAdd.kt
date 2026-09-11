package com.fishking.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DaveScreenAddAction(
    val section: FishKingSection,
    val enabled: State<Boolean>,
    val onClick: State<() -> Unit>,
)

/** Registrations are owned by a composition, not by the outgoing page's section name. */
internal class DaveScreenAddActions {
    private data class Registration(val owner: Any, val action: DaveScreenAddAction)
    private val registrations = mutableStateListOf<Registration>()

    fun register(owner: Any, action: DaveScreenAddAction) {
        unregister(owner)
        registrations.add(Registration(owner, action))
    }

    fun unregister(owner: Any) {
        registrations.removeAll { it.owner === owner }
    }

    fun forSection(section: FishKingSection): DaveScreenAddAction? =
        registrations.lastOrNull { it.action.section == section }?.action
}

internal val LocalDaveScreenAddActions = staticCompositionLocalOf<DaveScreenAddActions?> { null }

/**
 * Keep the actual creation action in its page while the shared frame paints the
 * unchanged Life-page button above page transitions, scroll clips and drag layers.
 * The fallback keeps standalone screens/previews usable without DavePageFrame.
 */
@Composable
fun DaveScreenFloatingAddAction(
    section: FishKingSection,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = LocalDaveScreenAddActions.current
    if (host == null) {
        DaveFloatingAddButton(enabled, onClick, modifier)
        return
    }
    val owner = remember { Any() }
    val currentEnabled = rememberUpdatedState(enabled)
    val currentClick = rememberUpdatedState(onClick)
    DisposableEffect(host, owner, section) {
        host.register(owner, DaveScreenAddAction(section, currentEnabled, currentClick))
        onDispose { host.unregister(owner) }
    }
}

/** A thumb-friendly add entry that stays directly after the list's last card. */
@Composable
fun DaveListInlineAddAction(
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = DavePalette.Ink.copy(alpha = if (enabled) .56f else .28f),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
        )
    }
}
