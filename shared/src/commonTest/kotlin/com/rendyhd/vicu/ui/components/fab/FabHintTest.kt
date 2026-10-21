package com.rendyhd.vicu.ui.components.fab

import com.rendyhd.vicu.data.local.FabHintStore
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Hold + to say a task" shows once, after the fifth task added with +. */
class FabHintTest {
    @Test
    fun theFifthAddMakesItDue() {
        var state = FabHintState()
        repeat(4) { state = state.afterPlusAdd() }
        assertFalse(state.isDue)
        state = state.afterPlusAdd()
        assertTrue(state.isDue)
        assertFalse(state.copy(shown = true).isDue)
        assertFalse(state.copy(voiceUsed = true).isDue)
    }

    private fun TestScope.hint(store: FabHintStore) = FabHint(store, this)

    @Test
    fun itShowsOnceAndNeverAgain() = runTest {
        val store = FabHintStore(InMemoryPreferencesDataStore())
        val hint = hint(store)
        repeat(4) {
            hint.onTaskAddedWithPlus()
            advanceUntilIdle()
        }
        assertFalse(hint.visible.value)
        hint.onTaskAddedWithPlus()
        advanceUntilIdle()
        assertTrue(hint.visible.value)
        assertTrue(store.read().shown)

        hint.dismiss()
        val again = hint(store)
        repeat(10) { again.onTaskAddedWithPlus() }
        advanceUntilIdle()
        assertFalse(again.visible.value)
    }

    @Test
    fun saidATaskAlreadyNoHint() = runTest {
        val store = FabHintStore(InMemoryPreferencesDataStore())
        val hint = hint(store)
        hint.onVoiceUsed()
        repeat(6) { hint.onTaskAddedWithPlus() }
        advanceUntilIdle()
        assertFalse(hint.visible.value)
        assertEquals(FabHintState(plusAdds = 5, shown = false, voiceUsed = true), store.read())
    }
}
