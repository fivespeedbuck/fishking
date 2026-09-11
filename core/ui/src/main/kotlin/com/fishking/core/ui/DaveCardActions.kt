package com.fishking.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Actions are painted in their owner's existing card, never on a second card. */
data class DaveCardAction(
    val icon: ImageVector,
    val description: String,
    val color: Color,
    val onClick: () -> Unit,
)

@Composable
fun DaveCardActionStrip(
    actions: List<DaveCardAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Box(
                modifier = Modifier.size(40.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClick = action.onClick)
                    .semantics { contentDescription = action.description },
                contentAlignment = Alignment.Center,
            ) { Icon(action.icon, null, tint = action.color, modifier = Modifier.size(21.dp)) }
        }
    }
}
