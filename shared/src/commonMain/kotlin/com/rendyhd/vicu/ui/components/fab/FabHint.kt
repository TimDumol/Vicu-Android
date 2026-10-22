package com.rendyhd.vicu.ui.components.fab

import com.rendyhd.vicu.data.local.FabHintStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The hint's counters: tasks added through the + button's quick add, and whether it is still needed. */
data class FabHintState(
    val plusAdds: Int = 0,
    val shown: Boolean = false,
    /** A task was added by holding + (or Say a task): the user knows already. */
    val voiceUsed: Boolean = false,
) {
    /** The hint is due: enough tasks added with +, never shown, never used. */
    val isDue: Boolean get() = !shown && !voiceUsed && plusAdds >= ADDS_BEFORE_HINT

    /** One more task added through the + button's quick add. */
    fun afterPlusAdd(): FabHintState = copy(plusAdds = (plusAdds + 1).coerceAtMost(ADDS_BEFORE_HINT))

    companion object {
        /** The hint shows after the fifth task added with +. */
        const val ADDS_BEFORE_HINT = 5
    }
}

/**
 * The one-time hint "Hold + to say a task" above the + button: after the fifth task added through
 * the + button's quick add, unless the user already added one by voice. It is marked shown when it
 * appears, so it never comes back, also when the app is closed while it shows.
 */
class FabHint(
    private val store: FabHintStore,
    private val scope: CoroutineScope,
) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()
    private val lock = Mutex()

    fun onTaskAddedWithPlus() = update { state ->
        val next = state.afterPlusAdd()
        if (next.isDue) {
            _visible.value = true
            next.copy(shown = true)
        } else {
            next
        }
    }

    fun onVoiceUsed() {
        _visible.value = false
        update { it.copy(voiceUsed = true) }
    }

    fun dismiss() {
        _visible.value = false
    }

    private fun update(change: (FabHintState) -> FabHintState) {
        scope.launch {
            lock.withLock {
                val current = store.read()
                val next = change(current)
                if (next != current) store.write(next)
            }
        }
    }
}
