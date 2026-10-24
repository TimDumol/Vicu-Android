package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.domain.model.RoutinePayload
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The rules of shared routines and of carrier writes that are data, not screens
 * (docs/cross-app-semantics-v1.md, sections 6.3, 6.8 and 6.9). Pure, so the vectors of
 * `routine-archive-v1.json` run against them.
 */
object SharedRoutines {

    /**
     * The `modifiedAt` of a placeholder: a `NOT_LOGGED` record an app writes on its own for a
     * missed day. Any record a person wrote wins the merge against it (section 6.3).
     */
    const val PLACEHOLDER_MODIFIED_AT = "1970-01-01T00:00:00.000Z"

    /**
     * True for a main carrier this account reads: its own, or another user's that is shared
     * (section 6.9). A carrier whose creator or whose account is not known yet counts as the
     * account's own, as everywhere a row was created offline or before `GET user` answered.
     */
    fun readsCarrier(createdBy: Long, me: Long?, shared: Boolean): Boolean =
        me == null || createdBy <= 0L || createdBy == me || shared

    /**
     * True for an archive part this account reads: one of a routine it reads ([readRoutines],
     * routine id to whether it is shared), whoever created it. A part a member wrote while the
     * routine was shared stays part of its history when the owner stops sharing it (section 6.9).
     */
    fun countsPart(routineId: String, readRoutines: Map<String, Boolean>): Boolean = routineId in readRoutines

    /**
     * What a write sends after the merge (section 6.8): where [merged] would replace the server's
     * record of an occurrence with one of the same status and note, the server's record stays, so
     * the occurrence keeps the record of whoever logged it first.
     */
    fun keepServerRecords(merged: RoutinePayload, remote: RoutinePayload): RoutinePayload {
        var changed = false
        val occurrences = merged.occurrences.mapValues { (key, record) ->
            val server = remote.occurrences[key]
            if (server != null && server != record && sameState(server, record)) {
                changed = true
                server
            } else {
                record
            }
        }
        return if (changed) merged.copy(occurrences = occurrences) else merged
    }

    /** How far back a refresh looks for this device's changes the server lost (section 6.8). */
    val REASSERT_WINDOW = 7.days

    /**
     * This device's changes that the server's copy lost (section 6.8): the records of [local]
     * that [deviceId] wrote since [since], and the definition when it wrote that since then, as
     * a payload to write again, or null when [server] already holds all of it (merged, nothing
     * changes). [local] is the copy the device last wrote or read; anything else in it is left
     * out, so an old copy never brings back what another device archived.
     */
    fun lostOwnChanges(local: RoutinePayload, server: RoutinePayload, deviceId: String, since: Instant): RoutinePayload? {
        if (local.definition.id != server.definition.id) return null
        fun recent(at: String) = parseInstantOrNull(at)?.let { it >= since } == true
        val own = local.occurrences.filterValues { it.modifiedBy == deviceId && recent(it.modifiedAt) }
        val ownDefinition = local.definition.updatedBy == deviceId && recent(local.definition.updatedAt)
        if (own.isEmpty() && !ownDefinition) return null
        val probe = server.copy(
            definition = if (ownDefinition) local.definition else server.definition,
            occurrences = own,
        )
        val merged = keepServerRecords(RoutineEnvelope.mergePayload(probe, server), server)
        return if (merged == server) null else probe
    }

    private fun parseInstantOrNull(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun sameState(a: RoutineOccurrenceRecord, b: RoutineOccurrenceRecord): Boolean =
        a.status == b.status && a.note == b.note
}
