package com.fishking.core.ui

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DaveScreenAddActionsTest {
    @Test fun outgoingPageCannotRemoveTheIncomingPagesAddAction() {
        val host = DaveScreenAddActions()
        val outgoing = Any()
        val incoming = Any()
        var created = ""
        host.register(outgoing, action(FishKingSection.HOME) { created = "day" })
        host.register(incoming, action(FishKingSection.HOME) { created = "week" })
        host.unregister(outgoing)
        host.forSection(FishKingSection.HOME)!!.onClick.value()
        assertEquals("week", created)
        host.unregister(incoming)
        assertNull(host.forSection(FishKingSection.HOME))
    }

    @Test fun anAnimatedOutgoingSectionNeverOwnsTheNewSectionsButton() {
        val host = DaveScreenAddActions()
        var created = ""
        host.register(Any(), action(FishKingSection.HOME) { created = "todo" })
        host.register(Any(), action(FishKingSection.JOURNAL) { created = "journal" })
        host.register(Any(), action(FishKingSection.HABIT) { created = "habit" })
        host.register(Any(), action(FishKingSection.LIFE) { created = "life" })
        listOf(FishKingSection.HOME to "todo", FishKingSection.JOURNAL to "journal",
            FishKingSection.HABIT to "habit", FishKingSection.LIFE to "life").forEach { (section, expected) ->
            host.forSection(section)!!.onClick.value()
            assertEquals(expected, created)
        }
    }

    @Test fun disabledDraftAndChangedDateUpdateWithoutRemovingTheVisibleAction() {
        val host = DaveScreenAddActions()
        val enabled = mutableStateOf(true)
        var created = ""
        val click = mutableStateOf<() -> Unit>({ created = "old date" })
        host.register(Any(), DaveScreenAddAction(FishKingSection.HOME, enabled, click))
        val sameAction = host.forSection(FishKingSection.HOME)!!
        enabled.value = false
        assertFalse(sameAction.enabled.value)
        click.value = { created = "selected date" }
        enabled.value = true
        assertTrue(sameAction.enabled.value)
        sameAction.onClick.value()
        assertEquals("selected date", created)
        assertTrue(host.forSection(FishKingSection.HOME) === sameAction)
    }

    private fun action(section: FishKingSection, onClick: () -> Unit) =
        DaveScreenAddAction(section, mutableStateOf(true), mutableStateOf(onClick))
}
