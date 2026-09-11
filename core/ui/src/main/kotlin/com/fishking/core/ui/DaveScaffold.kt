package com.fishking.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

enum class FishKingSection(val description: String) {
    HOME("日期主页"),
    JOURNAL("日记"),
    HABIT("习惯打卡"),
    LIFE("人生清单"),
}

@Composable
fun DaveGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val skin = LocalAppBackgroundSkin.current
    val targetTop = if (skin == AppBackgroundSkin.WARM_CREAM) DavePalette.CreamTop else DavePalette.MintTop
    val targetBottom = if (skin == AppBackgroundSkin.WARM_CREAM) DavePalette.CreamBottom else DavePalette.AquaBottom
    val top by androidx.compose.animation.animateColorAsState(
        targetValue = targetTop,
        animationSpec = androidx.compose.animation.core.tween(320),
        label = "background-skin-top",
    )
    val bottom by androidx.compose.animation.animateColorAsState(
        targetValue = targetBottom,
        animationSpec = androidx.compose.animation.core.tween(320),
        label = "background-skin-bottom",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        content()
    }
}

@Composable
fun DaveNavigationRow(
    selected: FishKingSection,
    onSelected: (FishKingSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FishKingSection.entries.forEach { section ->
            DaveNavigationButton(
                section = section,
                selected = section == selected,
                onClick = { onSelected(section) },
            )
        }
    }
}

@Composable
private fun DaveNavigationButton(
    section: FishKingSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fill = when (section) {
        FishKingSection.HOME -> DavePalette.NavHome
        FishKingSection.JOURNAL -> DavePalette.NavJournal
        FishKingSection.HABIT -> DavePalette.NavHabit
        FishKingSection.LIFE -> DavePalette.NavLife
    }
    Box(
        modifier = Modifier
            .size(if (selected) 64.dp else 58.dp)
            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(9.dp))
            .padding(5.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) fill else Color.White.copy(alpha = 0.75f),
                shape = RoundedCornerShape(6.dp),
            )
            .background(fill, RoundedCornerShape(5.dp))
            .clip(RoundedCornerShape(5.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = section.description },
        contentAlignment = Alignment.Center,
    ) {
        NavigationGlyph(section)
    }
}

@Composable
private fun NavigationGlyph(section: FishKingSection) {
    if (section == FishKingSection.HOME) {
        Text("!", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
    } else {
        Icon(when (section) {
            FishKingSection.JOURNAL -> Icons.Outlined.Edit
            FishKingSection.HABIT -> Icons.Outlined.Autorenew
            else -> Icons.Outlined.Flag
        }, null, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun DaveContextHeader(
    title: String,
    modifier: Modifier = Modifier,
    calendarEnabled: Boolean = false,
    onCalendarClick: () -> Unit = {},
    onCalendarLongPress: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingWidth: Dp = 43.dp,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = DavePalette.HeaderGreen,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (calendarEnabled) {
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .background(Color(0xFFBDEACB), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (onCalendarLongPress == null) Modifier.clickable(onClick = onCalendarClick)
                            else Modifier.pointerInput(onCalendarClick, onCalendarLongPress) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val outcome = withTimeoutOrNull(3000L) {
                                        var result = 0
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.isConsumed || event.changes.count { it.pressed } > 1 ||
                                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                            if (!change.pressed) { change.consume(); result = 1; break }
                                        }
                                        result
                                    }
                                    if (outcome == 1) onCalendarClick()
                                    else if (outcome == null) {
                                        onCalendarLongPress()
                                        do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } } while (event.changes.any { it.pressed })
                                    }
                                }
                            }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "选择日期",
                        tint = DavePalette.HeaderGreenDark,
                        modifier = Modifier.size(29.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(43.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Box(modifier = Modifier.width(trailingWidth).height(43.dp), contentAlignment = Alignment.Center) {
                trailing?.invoke()
            }
        }
    }
}

@Composable
fun DaveHomeViewButton(
    weekView: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(39.dp)
            .background(Color.White.copy(alpha = .18f), RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (weekView) Icons.Outlined.CalendarViewDay else Icons.Outlined.ViewWeek,
            contentDescription = if (weekView) "切换到单日视图" else "切换到整周视图",
            tint = Color.White,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
fun DaveTodayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(39.dp)
            .background(Color.White.copy(alpha = .18f), RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Today,
            contentDescription = "回到今天",
            tint = Color.White,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
fun DavePageFrame(
    selectedSection: FishKingSection,
    onSectionSelected: (FishKingSection) -> Unit,
    header: @Composable () -> Unit,
    showNavigation: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val addActions = remember { DaveScreenAddActions() }
    CompositionLocalProvider(LocalDaveScreenAddActions provides addActions) {
    DaveGradientBackground(modifier) {
        Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            header()
            if (showNavigation) DaveNavigationRow(selectedSection, onSectionSelected)
            DaveDragLayer(Modifier.weight(1f)) { content() }
        }
        val action = addActions.forSection(selectedSection)
        if (showNavigation && action != null) {
            // Keep the Life-page size, color and 22dp screen-edge placement.
            // This sibling is outside AnimatedContent and DaveDragLayer; neither
            // a clipped list nor a dragged/returning card can cover the button.
            Box(Modifier.matchParentSize().imePadding().zIndex(1f)) {
                DaveFloatingAddButton(
                    enabled = action.enabled.value,
                    onClick = { action.onClick.value.invoke() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
                )
            }
        }
        }
    }
    }
}

@Composable
fun DaveEmptyPanel(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = DavePalette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(detail, color = DavePalette.Ink.copy(alpha = .62f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
