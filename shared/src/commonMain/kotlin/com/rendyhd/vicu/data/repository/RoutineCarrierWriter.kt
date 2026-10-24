package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.data.remote.api.TaskDto
import com.rendyhd.vicu.data.remote.api.VikunjaApiException
import com.rendyhd.vicu.data.remote.api.VikunjaApiService
import com.rendyhd.vicu.domain.model.RoutinePayload
import com.rendyhd.vicu.util.Constants
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.RoutineEnvelope
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.milliseconds

/**
 * Writes routine carriers so that two writers never overwrite each other
 * (docs/cross-app-semantics-v1.md, section 6.8): read the server's copy, merge, and send a JSON
 * Patch whose first operation tests that the description is still the one that was read. When
 * another device or person wrote in between, Vikunja applies nothing and answers 422; the write
 * reads again, merges again and tries again.
 *
 * Vikunja checks the test and stores the result in two steps, so two writes that arrive at the
 * same moment can both pass it, and the later one then removes what the earlier one wrote (on an
 * SQLite server one of them fails with a 500 instead). So a write that went through waits
 * [VERIFY_DELAY], reads the carrier again and checks that it still holds everything that was
 * sent; when it does not, it writes the merge again. A 5xx answer is retried like a 422.
 * [MAX_WRITES] writes in all.
 *
 * Every routine carrier write goes through here: an edit sent at once
 * ([TaskRepositoryImpl.update]), a queued edit replayed by the sync, sharing and stopping
 * (`RoutineRepositoryImpl`), and a refresh that finds this device's ticks missing
 * (`TaskRefresher`). Archive parts use [writeArchivePart].
 */
class RoutineCarrierWriter(
    private val api: VikunjaApiService,
    private val json: Json,
) {
    companion object {
        const val MAX_WRITES = 4

        /** Long enough for a write that passed the test together with this one to be stored. */
        val VERIFY_DELAY = 500.milliseconds

        /** Before another try after a 5xx (an SQLite server that was busy), times the try. */
        val SERVER_ERROR_DELAY = 250.milliseconds

        private const val TAG = "RoutineCarrierWriter"

        /** The write kept being undone or refused. */
        const val KEPT_CHANGING = "This routine kept changing on the server, so the change was not saved. Try again."

        private fun retryable(e: VikunjaApiException) =
            e.httpStatus == VikunjaApiService.PATCH_NOT_APPLIED || e.httpStatus >= 500
    }

    /** What [write] did: the server's answer, or null when the server's task holds no routine. */
    data class Outcome(val task: TaskDto?)

    /**
     * Writes the routine in [localDescription] over the carrier [taskId]. [change] adjusts the
     * merged payload before it is sent (sharing sets the flag); [projectId] moves the carrier in
     * the same request. Returns an [Outcome] without a task when the server's task holds no
     * routine any more, so the caller sends its plain edit. Throws
     * [RoutineEnvelope.UnreadableCarrierException] when either side is a carrier this version
     * cannot read, and a [VikunjaApiException] when the write was refused or kept being undone.
     */
    suspend fun write(
        taskId: Long,
        localDescription: String,
        projectId: Long? = null,
        change: (RoutinePayload) -> RoutinePayload = { it },
    ): Outcome {
        // What the server must hold: the local change, and after a write, what was sent.
        var wanted = RoutineEnvelope.parse(localDescription, json)
        var sent = false
        var writes = 0
        var lastFailure: VikunjaApiException? = null
        while (true) {
            val remote = api.getTask(taskId)
            val remoteParsed = RoutineEnvelope.parse(remote.description, json)
            val merged = RoutineEnvelope.mergeForWrite(wanted, remoteParsed) ?: return Outcome(null)
            if (sent) {
                if (merged == remoteParsed.payload) return Outcome(remote)
                Logger.w(TAG, "Routine carrier $taskId lost part of a write made at the same moment as another; writing again")
            }
            if (writes == MAX_WRITES) throw lastFailure ?: VikunjaApiException(HttpStatusCode.Conflict.value, message = KEPT_CHANGING)
            writes++
            val payload = if (sent) merged else change(merged)
            val description = RoutineEnvelope.upsert(remoteParsed.body, payload, json)
            val operations = buildJsonArray {
                add(op("test", "description", JsonPrimitive(remote.description)))
                add(op("replace", "title", JsonPrimitive(payload.definition.name)))
                add(op("replace", "description", JsonPrimitive(description)))
                // The shape every hidden task is written with (section 10.2).
                add(op("replace", "done", JsonPrimitive(true)))
                add(op("replace", "due_date", JsonPrimitive(Constants.NULL_DATE_STRING)))
                add(op("replace", "repeat_after", JsonPrimitive(0)))
                add(op("replace", "repeat_mode", JsonPrimitive(0)))
                add(op("replace", "reminders", JsonArray(emptyList())))
                if (projectId != null) add(op("replace", "project_id", JsonPrimitive(projectId)))
            }
            try {
                api.patchTask(taskId, operations)
                wanted = RoutineEnvelope.parse(description, json)
                sent = true
                delay(VERIFY_DELAY)
            } catch (e: VikunjaApiException) {
                if (!retryable(e)) throw e
                // Written by someone else since it was read: read it again and merge once more.
                Logger.d(TAG, "Routine carrier $taskId not written (HTTP ${e.httpStatus}, write $writes)")
                lastFailure = VikunjaApiException(HttpStatusCode.Conflict.value, message = KEPT_CHANGING)
                if (e.httpStatus >= 500) delay(SERVER_ERROR_DELAY * writes)
            }
        }
    }

    /**
     * Writes an archive part's description, conditional on the description read (section 6.8),
     * and checks it the same way as [write]. [build] makes the new description from the part as
     * read, or returns null when the part already holds the change; it runs again on every read.
     */
    suspend fun writeArchivePart(taskId: Long, build: (TaskDto) -> String?): TaskDto {
        var writes = 0
        while (true) {
            val read = api.getTask(taskId)
            val description = build(read) ?: return read
            if (writes == MAX_WRITES) throw VikunjaApiException(HttpStatusCode.Conflict.value, message = KEPT_CHANGING)
            writes++
            val operations = buildJsonArray {
                add(op("test", "description", JsonPrimitive(read.description)))
                add(op("replace", "description", JsonPrimitive(description)))
            }
            try {
                api.patchTask(taskId, operations)
                delay(VERIFY_DELAY)
            } catch (e: VikunjaApiException) {
                if (!retryable(e)) throw e
                if (e.httpStatus >= 500) delay(SERVER_ERROR_DELAY * writes)
            }
        }
    }

    private fun op(name: String, field: String, value: JsonElement) = buildJsonObject {
        put("op", name)
        put("path", "/$field")
        put("value", value)
    }
}
