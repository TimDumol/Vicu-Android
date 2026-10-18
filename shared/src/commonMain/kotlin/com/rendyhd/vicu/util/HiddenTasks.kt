package com.rendyhd.vicu.util

import com.rendyhd.vicu.data.remote.api.TaskDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

/**
 * The rules every hidden task follows, whatever its kind (docs/cross-app-semantics-v1.md,
 * section 10): the custom-list carrier, routine carriers and archive parts, the preferences
 * carrier, and kinds a later version adds.
 */
object HiddenTasks {
    /**
     * Any `<!-- vicu-<kind>:` marker, known or not, so a kind added later stays hidden here too.
     * Only space, tab, CR and LF count as whitespace, as on the desktop.
     */
    private val markerRegex = Regex("<!--[ \\t\\r\\n]*vicu-[a-z0-9-]+:")

    /** Vikunja's "no date". */
    const val NULL_DATE = "0001-01-01T00:00:00Z"

    /** Deleted records (tombstones) older than this are dropped before a write (section 10.6). */
    const val TOMBSTONE_MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000

    /** True when [description] holds a hidden-task marker: the task is left out of every list. */
    fun isHidden(description: String?): Boolean =
        !description.isNullOrEmpty() && markerRegex.containsMatchIn(description)

    /** True when [userId] created [task]. An unknown user owns nothing. */
    fun isOwn(task: TaskDto, userId: Long?): Boolean =
        userId != null && userId > 0L && task.createdBy?.id == userId

    /**
     * The fields every write of a hidden task sends again, so a due date, repeat, reminder or
     * reopening someone set in Vikunja's web UI is undone (section 10.2).
     */
    fun putForcedFields(builder: JsonObjectBuilder) {
        builder.put("done", true)
        builder.put("due_date", NULL_DATE)
        builder.put("repeat_after", 0)
        builder.put("repeat_mode", 0)
        builder.put("reminders", JsonArray(emptyList()))
    }

    /** True when a record stamped at [wallTimeMs] is too old to keep as a tombstone at [nowMs]. */
    fun isExpiredTombstone(wallTimeMs: Long, nowMs: Long): Boolean = wallTimeMs < nowMs - TOMBSTONE_MAX_AGE_MS
}
