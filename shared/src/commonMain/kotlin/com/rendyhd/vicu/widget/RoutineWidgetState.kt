package com.rendyhd.vicu.widget

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineDay
import com.rendyhd.vicu.util.DateDisplay
import com.rendyhd.vicu.util.DateDisplayFormat
import kotlinx.datetime.LocalTime
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
    /** The time of the occurrence as the widget shows it (the name is kept for the iOS widget's JSON). */
    val slotLabel: String,
    val status: OccurrenceStatus,
    /** The routine's own colour as it stores it (#RRGGBB); empty without one. */
    val color: String = "",
    /**
     * [color] as the check ring of the iOS widget on its light and dark background, worked out by
     * [WidgetSnapshots.routineDay]; empty without a colour. The Android widget works its own out
     * from [color], because it can follow the wallpaper colours.
     */
    val markLight: String = "",
    val markDark: String = "",
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
        fun from(day: RoutineDay, format: DateDisplayFormat): RoutineWidgetState = RoutineWidgetState(
            date = day.date,
            occurrences = day.occurrences.map { occurrence ->
                RoutineWidgetItem(
                    key = occurrence.key,
                    routineId = occurrence.routine.definition.id,
                    routineName = occurrence.routine.definition.name,
                    scheduledDate = occurrence.scheduledDate,
                    slotId = occurrence.slot.id,
                    slotLabel = DateDisplay.formatTime(
                        LocalTime(occurrence.slot.reminderMinutes / 60, occurrence.slot.reminderMinutes % 60),
                        format,
                    ),
                    status = occurrence.status,
                    color = occurrence.routine.definition.color,
                )
            },
        )
    }
}
