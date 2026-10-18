package com.rendyhd.vicu.auth

/**
 * Who is signed in, as far as hidden tasks care: a hidden task belongs to the user who created it,
 * and the app reads, merges, writes and deletes only its own (docs/cross-app-semantics-v1.md,
 * section 10.3).
 */
interface CurrentUser {
    /** The stored user id, without a request; null until it is known. */
    suspend fun knownId(): Long?

    /** The user id, read once with `GET user` when none is stored yet; null when that fails. */
    suspend fun id(): Long?

    companion object {
        /** A fixed answer, for tests and previews. */
        fun fixed(id: Long?): CurrentUser = object : CurrentUser {
            override suspend fun knownId(): Long? = id
            override suspend fun id(): Long? = id
        }
    }
}
