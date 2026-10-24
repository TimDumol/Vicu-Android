package com.rendyhd.vicu.data.remote.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VikunjaProblemDto(
    val title: String = "",
    val status: Int = 0,
    val detail: String = "",
    val code: Long = 0,
    val errors: List<VikunjaErrorDetailDto>? = null,
)

@Serializable
data class VikunjaErrorDetailDto(
    val location: String = "",
    val message: String = "",
    val value: JsonElement? = null,
)

class VikunjaApiException(
    val httpStatus: Int,
    val problem: VikunjaProblemDto? = null,
    /**
     * The API token lacks the permission for this request while the session still works
     * (cross-app semantics 9.7). [httpStatus] is then 403, and the message says what to do.
     */
    val tokenPermission: Boolean = false,
    message: String? = null,
) : Exception(
    message
        ?: problem?.detail?.takeIf { it.isNotBlank() }
        ?: problem?.title?.takeIf { it.isNotBlank() }
        ?: "Vikunja request failed (HTTP $httpStatus)",
)

/**
 * True when this is the server's answer to a `q` search while the session still works: the
 * server's search is broken, not the connection or the sign-in. A ParadeDB `pg_search` extension
 * older than the library Postgres loads answers every search with a 500 ("function
 * paradedb.ctid_is_valid(tid) does not exist") while listings without `q` keep working, so the
 * sync lists without `q` instead of failing.
 */
val VikunjaApiException.isBrokenSearch: Boolean get() = httpStatus != 401
