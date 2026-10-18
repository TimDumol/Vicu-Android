package com.rendyhd.vicu.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class RoutineKind {
    HEALTH,
    CHORE,
}

@Serializable
enum class HealthSubtype {
    SUPPLEMENT,
    MEDICATION,
}

@Serializable
enum class RoutinePeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    ANYTIME,
    HOME,
}

@Serializable
enum class OccurrenceStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
    NOT_LOGGED,
}

/** Done for the day: completed or skipped. Pending and not-logged occurrences still need attention. */
val OccurrenceStatus.isFinished: Boolean
    get() = this == OccurrenceStatus.COMPLETED || this == OccurrenceStatus.SKIPPED

@Serializable
sealed class RoutineSchedule {
    @Serializable
    @SerialName("calendar")
    data class Calendar(
        /** ISO-8601 weekdays, Monday=1 through Sunday=7. Empty means every day. */
        val weekdays: Set<Int> = emptySet(),
        val weekInterval: Int = 1,
        val anchorDate: String,
    ) : RoutineSchedule()

    @Serializable
    @SerialName("after_completion")
    data class AfterCompletion(
        val intervalDays: Int,
        val firstDueDate: String,
    ) : RoutineSchedule()
}

@Serializable
data class RoutineSlot(
    val id: String,
    val label: String,
    val period: RoutinePeriod,
    /** Minutes after local midnight. */
    val reminderMinutes: Int = 8 * 60,
    val reminderEnabled: Boolean = true,
    /** Zero disables the follow-up. */
    val followUpMinutes: Int = 30,
    /** Fields a newer app added, written back unchanged (docs/cross-app-semantics-v1.md, section 10.8). */
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

@Serializable
data class RoutineDefinition(
    val id: String,
    val name: String,
    val kind: RoutineKind,
    val healthSubtype: HealthSubtype? = null,
    val amount: String = "",
    val unit: String = "",
    val iconName: String = "",
    val color: String = "",
    val schedule: RoutineSchedule,
    val slots: List<@Serializable(with = RoutineSlotSerializer::class) RoutineSlot>,
    val activeFrom: String,
    val archived: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

@Serializable
data class RoutineOccurrenceRecord(
    val key: String,
    val routineId: String,
    val slotId: String,
    val scheduledDate: String,
    val scheduledMinutes: Int,
    val timeZoneId: String,
    val status: OccurrenceStatus,
    val loggedAt: String = "",
    val modifiedAt: String,
    val modifiedBy: String,
    val note: String = "",
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

@Serializable
data class RoutinePayload(
    val version: Int = 1,
    @Serializable(with = RoutineDefinitionSerializer::class) val definition: RoutineDefinition,
    val occurrences: Map<String, @Serializable(with = RoutineOccurrenceSerializer::class) RoutineOccurrenceRecord> = emptyMap(),
    val prunedBefore: String = "",
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

/**
 * One archive part: older occurrences of one routine, held by a hidden done task on the server
 * (docs/cross-app-semantics-v1.md, section 6.4). A routine can have any number of parts; reading
 * its history merges the main carrier with every part by key.
 */
@Serializable
data class RoutineArchivePart(
    val version: Int = 1,
    val routineId: String,
    /** 1-based; duplicates of a number are allowed and merge like any other part. */
    val part: Int,
    val occurrences: Map<String, @Serializable(with = RoutineOccurrenceSerializer::class) RoutineOccurrenceRecord> = emptyMap(),
    @SerialName(UNKNOWN_FIELDS_KEY) val unknown: JsonObject = NO_UNKNOWN_FIELDS,
)

// Routine payloads keep the fields a newer app added at every level, like custom lists do. Encode
// and decode a payload or an archive part through these, never the plain class serializer.
internal object RoutineSlotSerializer : UnknownFieldsSerializer<RoutineSlot>(RoutineSlot.serializer())
internal object RoutineDefinitionSerializer : UnknownFieldsSerializer<RoutineDefinition>(RoutineDefinition.serializer())
internal object RoutineOccurrenceSerializer : UnknownFieldsSerializer<RoutineOccurrenceRecord>(RoutineOccurrenceRecord.serializer())
internal object RoutinePayloadSerializer : UnknownFieldsSerializer<RoutinePayload>(RoutinePayload.serializer())
internal object RoutineArchivePartSerializer : UnknownFieldsSerializer<RoutineArchivePart>(RoutineArchivePart.serializer())

data class Routine(
    val taskId: Long,
    val payload: RoutinePayload,
) {
    val definition: RoutineDefinition get() = payload.definition
}

data class RoutineOccurrence(
    val routine: Routine,
    val key: String,
    val slot: RoutineSlot,
    val scheduledDate: String,
    val status: OccurrenceStatus,
    val loggedAt: String = "",
    val note: String = "",
    val overdue: Boolean = false,
)

data class RoutineDay(
    val date: String,
    val occurrences: List<RoutineOccurrence>,
) {
    val scheduledCount: Int get() = occurrences.size
    val completedCount: Int get() = occurrences.count { it.status == OccurrenceStatus.COMPLETED }

    /** What is still to do today; Today lists only these. */
    val open: List<RoutineOccurrence> get() = occurrences.filterNot { it.status.isFinished }
}

data class RoutineDraft(
    val name: String,
    val kind: RoutineKind,
    val healthSubtype: HealthSubtype? = null,
    val amount: String = "",
    val unit: String = "",
    val iconName: String = "",
    val color: String = "",
    val schedule: RoutineSchedule,
    val slots: List<RoutineSlot>,
)
