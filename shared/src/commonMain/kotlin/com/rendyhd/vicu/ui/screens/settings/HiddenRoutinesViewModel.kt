package com.rendyhd.vicu.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The routines others shared with this account that it hid (docs/cross-app-semantics-v1.md,
 * section 6.9), for the routine settings, where they can be shown again.
 */
class HiddenRoutinesViewModel(private val repository: RoutineRepository) : ViewModel() {

    val hidden: StateFlow<List<Routine>> = repository.observeHidden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun show(routine: Routine) {
        viewModelScope.launch { repository.setHidden(routine.definition.id, false) }
    }
}
