@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.RoutineArchivePart
import com.rendyhd.vicu.domain.model.RoutineArchivePartSerializer
import com.rendyhd.vicu.domain.model.RoutinePayload
import com.rendyhd.vicu.domain.model.RoutinePayloadSerializer
import com.rendyhd.vicu.domain.model.RoutineSchedule
import kotlin.io.encoding.Base64
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object RoutineEnvelope {
    const val CURRENT_VERSION = 1
    private const val MAX_DECODED_BYTES = 512 * 1024
    private val markerRegex = Regex(
        """<!--\s*vicu-routine:v(\d+):([A-Za-z0-9_-]+={0,2})\s*-->""",
    )

    /** Any main carrier marker. The lookahead keeps archive part markers out of it. */
    private val anyMarkerRegex = Regex(
        """<!--\s*vicu-routine:(?!archive:)[\s\S]*?-->""",
    )
    private val archiveMarkerRegex = Regex(
        """<!--\s*vicu-routine:archive:v(\d+):([A-Za-z0-9_-]+={0,2})\s*-->""",
    )
    private val anyArchiveMarkerRegex = Regex(
        """<!--\s*vicu-routine:archive:[\s\S]*?-->""",
    )

    /** Every routine marker, main carrier or archive part: what hides a task from the lists. */
    private val anyMetadataRegex = Regex(
        """<!--\s*vicu-routine:[\s\S]*?-->""",
    )

    data class Parsed(
        val isCarrier: Boolean,
        val body: String,
        val rawMarker: String = "",
        val payload: RoutinePayload? = null,
        val error: String? = null,
        val version: Int? = null,
    ) {
        /** A newer version wrote it: this version must not write it (section 10.7). */
        val isNewerVersion: Boolean get() = (version ?: 0) > CURRENT_VERSION
    }

    /** Thrown instead of writing over a routine carrier this version cannot read (section 10.7). */
    class UnreadableCarrierException(message: String) : IllegalStateException(message)

    data class ParsedArchive(
        val isArchive: Boolean,
        val part: RoutineArchivePart? = null,
        val error: String? = null,
    )

    /**
     * True for any routine metadata task: a main carrier or an archive part. This is the check
     * that hides such a task from every list, search, badge and count; use [hasCarrierMarker]
     * to ask for a routine in particular.
     */
    fun hasMarker(description: String?): Boolean =
        !description.isNullOrEmpty() && anyMetadataRegex.containsMatchIn(description)

    /** True when the description holds a main carrier marker (not an archive part). */
    fun hasCarrierMarker(description: String?): Boolean =
        !description.isNullOrEmpty() && anyMarkerRegex.containsMatchIn(description)

    /** True when the description holds a routine archive part marker. */
    fun hasArchiveMarker(description: String?): Boolean =
        !description.isNullOrEmpty() && anyArchiveMarkerRegex.containsMatchIn(description)

    /** The routine marker of either kind, so an editor can keep it while it edits the rest. */
    fun extractMarker(description: String?): String =
        if (description.isNullOrEmpty()) "" else anyMetadataRegex.find(description)?.value.orEmpty()

    /** Reads a main carrier description. An archive part is not a carrier (`isCarrier` false). */
    fun parse(description: String?, json: Json): Parsed {
        if (description.isNullOrEmpty()) return Parsed(false, "")
        val any = anyMarkerRegex.find(description) ?: return Parsed(false, description)
        val body = description.removeRange(any.range).trimEnd()
        val marker = markerRegex.matchEntire(any.value)
            ?: return Parsed(true, body, any.value, error = "Malformed routine metadata")
        val version = marker.groupValues[1].toIntOrNull()
            ?: return Parsed(true, body, any.value, error = "Invalid routine version")
        if (version != CURRENT_VERSION) {
            return Parsed(
                isCarrier = true,
                body = body,
                rawMarker = any.value,
                error = "Routine metadata version $version is not supported",
                version = version,
            )
        }
        return runCatching {
            val encoded = marker.groupValues[2]
            val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
            val bytes = Base64.UrlSafe.decode(padded)
            require(bytes.size <= MAX_DECODED_BYTES) { "Routine metadata is too large" }
            val payload = json.decodeFromString(RoutinePayloadSerializer, bytes.decodeToString())
            if (payload.version > CURRENT_VERSION) {
                return Parsed(
                    isCarrier = true,
                    body = body,
                    rawMarker = any.value,
                    error = "Routine metadata version ${payload.version} is not supported",
                    version = payload.version,
                )
            }
            require(payload.version == CURRENT_VERSION) { "Routine payload version mismatch" }
            require(payload.definition.id.isNotBlank()) { "Routine id is missing" }
            require(payload.definition.name.isNotBlank()) { "Routine name is missing" }
            require(payload.definition.slots.isNotEmpty()) { "Routine has no slots" }
            require(payload.definition.slots.all { it.id.isNotBlank() }) { "Routine slot id is missing" }
            require(payload.definition.slots.map { it.id }.distinct().size == payload.definition.slots.size) {
                "Routine slot ids must be unique"
            }
            LocalDate.parse(payload.definition.activeFrom)
            when (val schedule = payload.definition.schedule) {
                is RoutineSchedule.Calendar -> LocalDate.parse(schedule.anchorDate)
                is RoutineSchedule.AfterCompletion -> {
                    require(schedule.intervalDays > 0) { "Routine interval must be positive" }
                    LocalDate.parse(schedule.firstDueDate)
                }
            }
            Parsed(true, body, any.value, payload, version = version)
        }.getOrElse { error ->
            Parsed(
                isCarrier = true,
                body = body,
                rawMarker = any.value,
                error = error.message ?: "Cannot decode routine metadata",
                version = version,
            )
        }
    }

    fun encode(payload: RoutinePayload, json: Json): String {
        val bytes = json.encodeToString(RoutinePayloadSerializer, payload).encodeToByteArray()
        require(bytes.size <= MAX_DECODED_BYTES) { "Routine metadata is too large" }
        val encoded = Base64.UrlSafe.encode(bytes).trimEnd('=')
        return "<!-- vicu-routine:v$CURRENT_VERSION:$encoded -->"
    }

    /** Replaces the main carrier marker in [descriptionBody]; an archive marker is left alone. */
    fun upsert(descriptionBody: String, payload: RoutinePayload, json: Json): String {
        val cleanBody = anyMarkerRegex.replace(descriptionBody, "").trimEnd()
        val marker = encode(payload, json)
        return if (cleanBody.isEmpty()) marker else "$cleanBody\n$marker"
    }

    /** The description without any routine marker, main or archive. */
    fun strip(description: String?): String {
        if (description.isNullOrEmpty()) return ""
        return anyMetadataRegex.replace(description, "").trim()
    }

    /**
     * The merge used before a carrier is sent to Vikunja (section 6.3 of the cross-app contract):
     * `prunedBefore` is the later of the two, occurrences are united by key with last-write-wins
     * on the parsed `modifiedAt`, and whatever is older than `prunedBefore` is dropped because it
     * lives in the archive. The definition is last-write-wins on `updatedAt`. [remote] is the server's
     * copy: of the fields this version does not know at the top level, both sides' are kept and the
     * server's value wins (section 10.8); below that they travel with the record that wins.
     */
    fun mergePayload(local: RoutinePayload, remote: RoutinePayload): RoutinePayload {
        return mergeInputs(local, remote)
    }

    /**
     * What a write of the routine [local] may send over the server's copy [remote]: their merge,
     * keeping the server's record of an occurrence already in the same state (section 6.8), or
     * null when the server's task holds no routine any more (the plain edit goes then). Throws
     * [UnreadableCarrierException] when either side is a carrier this version cannot read, a newer
     * version's or a damaged one: writing would replace what it holds (section 10.7).
     */
    fun mergeForWrite(local: Parsed, remote: Parsed): RoutinePayload? {
        if (local.payload != null && remote.payload != null) {
            return SharedRoutines.keepServerRecords(mergePayload(local.payload, remote.payload), remote.payload)
        }
        if (!remote.isCarrier && local.payload != null) return null
        val newer = local.isNewerVersion || remote.isNewerVersion
        throw UnreadableCarrierException(
            if (newer) {
                "This routine was saved by a newer version of Vicu. Update Vicu to change it."
            } else {
                "This routine's record cannot be read, so it was not changed: ${remote.error ?: local.error ?: "unknown error"}"
            },
        )
    }

    private fun mergeInputs(local: RoutinePayload, remote: RoutinePayload): RoutinePayload {
        val merged = RoutineScheduleEngine.merge(
            RoutinePayloadMergeInput(local.definition, local.occurrences, local.prunedBefore),
            RoutinePayloadMergeInput(remote.definition, remote.occurrences, remote.prunedBefore),
        )
        return local.copy(
            version = maxOf(local.version, remote.version),
            definition = merged.definition,
            occurrences = merged.occurrences,
            prunedBefore = merged.prunedBefore,
            unknown = JsonObject(local.unknown + remote.unknown),
        )
    }

    // --- Archive parts (section 6.4) ---------------------------------------------------------

    /** Reads an archive part task description. Anything without an archive marker is not one. */
    fun parseArchive(description: String?, json: Json): ParsedArchive {
        if (description.isNullOrEmpty()) return ParsedArchive(false)
        val any = anyArchiveMarkerRegex.find(description) ?: return ParsedArchive(false)
        val marker = archiveMarkerRegex.matchEntire(any.value)
            ?: return ParsedArchive(true, error = "Malformed routine archive metadata")
        val version = marker.groupValues[1].toIntOrNull()
            ?: return ParsedArchive(true, error = "Invalid routine archive version")
        if (version != CURRENT_VERSION) {
            return ParsedArchive(true, error = "Routine archive version $version is not supported")
        }
        return runCatching {
            val encoded = marker.groupValues[2]
            val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
            val bytes = Base64.UrlSafe.decode(padded)
            require(bytes.size <= MAX_DECODED_BYTES) { "Routine archive is too large" }
            val part = json.decodeFromString(RoutineArchivePartSerializer, bytes.decodeToString())
            require(part.version == CURRENT_VERSION) { "Routine archive version mismatch" }
            require(part.routineId.isNotBlank()) { "Routine archive has no routine id" }
            require(part.part >= 1) { "Routine archive part number is invalid" }
            // The key is also the map key; a record that lost its own copy gets it back.
            val occurrences = part.occurrences.mapValues { (key, record) ->
                if (record.key.isBlank()) record.copy(key = key) else record
            }
            ParsedArchive(true, part.copy(occurrences = occurrences))
        }.getOrElse { error ->
            ParsedArchive(true, error = error.message ?: "Cannot decode routine archive metadata")
        }
    }

    /** The description of an archive part task: only the marker (section 6.4). */
    fun encodeArchive(part: RoutineArchivePart, json: Json): String {
        val bytes = json.encodeToString(RoutineArchivePartSerializer, part).encodeToByteArray()
        require(bytes.size <= MAX_DECODED_BYTES) { "Routine archive part is too large" }
        val encoded = Base64.UrlSafe.encode(bytes).trimEnd('=')
        return "<!-- vicu-routine:archive:v$CURRENT_VERSION:$encoded -->"
    }
}
