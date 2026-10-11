package com.rendyhd.vicu.widget

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineDay
import kotlinx.serialization.Serializable

/**
 * What the routine widget shows: one day's occurrences with their status. Shared, so Android's
 * Glance widget and the iOS widget snapshot are built the same way.
 */
@Serializable
data class RoutineWidgetItem(
    val key: String,
    val routineId: String,
    val routineName: String,
    val scheduledDate: String,
    val slotId: String,
    val slotLabel: String,
    val status: OccurrenceStatus,
)

@Serializable
data class RoutineWidgetState(
    val date: String = "",
    val occurrences: List<RoutineWidgetItem> = emptyList(),
) {
    val scheduledCount: Int get() = occurrences.size
    val completedCount: Int get() = occurrences.count { it.status == OccurrenceStatus.COMPLETED }
    val visibleOccurrences: List<RoutineWidgetItem>
        get() = occurrences.filterNot { it.status == OccurrenceStatus.COMPLETED }
    val isAllDone: Boolean get() = occurrences.isNotEmpty() && visibleOccurrences.isEmpty()

    fun withStatus(
        routineId: String,
        date: String,
        slotId: String,
        status: OccurrenceStatus,
    ): RoutineWidgetState = copy(
        occurrences = occurrences.map { occurrence ->
            if (
                occurrence.routineId == routineId &&
                occurrence.scheduledDate == date &&
                occurrence.slotId == slotId
            ) {
                occurrence.copy(status = status)
            } else {
                occurrence
            }
        },
    )

    companion object {
        fun from(day: RoutineDay): RoutineWidgetState = RoutineWidgetState(
            date = day.date,
            occurrences = day.occurrences.map { occurrence ->
                RoutineWidgetItem(
                    key = occurrence.key,
                    routineId = occurrence.routine.definition.id,
                    routineName = occurrence.routine.definition.name,
                    scheduledDate = occurrence.scheduledDate,
                    slotId = occurrence.slot.id,
                    slotLabel = occurrence.slot.label,
                    status = occurrence.status,
                )
            },
        )
    }
}
