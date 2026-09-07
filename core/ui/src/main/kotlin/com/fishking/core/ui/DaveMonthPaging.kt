package com.fishking.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged

/** Load at most one neighbouring month per scroll gesture; stable item keys retain position. */
@Composable
fun DaveMonthPaging(state: LazyListState, enabled: Boolean, previous: () -> Unit, next: () -> Unit) {
    val previousAction by rememberUpdatedState(previous)
    val nextAction by rememberUpdatedState(next)
    LaunchedEffect(state, enabled) {
        var loaded = false
        snapshotFlow { Triple(state.isScrollInProgress, state.canScrollBackward, state.canScrollForward) }
            .distinctUntilChanged().collect { (scrolling, canGoBack, canGoForward) ->
                if (!scrolling) loaded = false
                if (enabled && scrolling && !loaded && state.layoutInfo.totalItemsCount > 0) {
                    when {
                        !canGoBack -> { loaded = true; previousAction() }
                        !canGoForward -> { loaded = true; nextAction() }
                    }
                }
            }
    }
}
