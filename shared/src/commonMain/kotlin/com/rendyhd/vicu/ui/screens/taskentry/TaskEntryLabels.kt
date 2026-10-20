package com.rendyhd.vicu.ui.screens.taskentry

import com.rendyhd.vicu.domain.model.Label
import com.rendyhd.vicu.domain.repository.LabelRepository
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.NetworkResult

/**
 * The ids of the labels a new task's text names: an existing label by case-insensitive title, a
 * new one created otherwise. Best-effort: a label that cannot be created is logged and left out,
 * so the task itself still saves.
 */
internal suspend fun resolveParsedLabels(
    names: List<String>,
    known: List<Label>,
    labelRepository: LabelRepository,
): Set<Long> {
    val ids = mutableSetOf<Long>()
    for (name in names) {
        val matched = known.find { it.title.equals(name, ignoreCase = true) }
        if (matched != null) {
            ids.add(matched.id)
            continue
        }
        when (val res = labelRepository.create(Label(id = 0L, title = name, hexColor = ""))) {
            is NetworkResult.Success -> ids.add(res.data.id)
            is NetworkResult.Error -> Logger.w("TaskEntryLabels", "Auto-create label '$name' failed: ${res.message}")
            is NetworkResult.Loading -> {}
        }
    }
    return ids
}
