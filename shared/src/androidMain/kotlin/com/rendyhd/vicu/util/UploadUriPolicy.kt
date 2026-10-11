package com.rendyhd.vicu.util

/** File pickers and share senders must provide a grantable, external content URI. */
internal fun isExternalUploadUri(scheme: String?, authority: String?, packageName: String): Boolean =
    scheme == "content" && !authority.isNullOrBlank() &&
        // Android permits a user-id prefix on a content authority.
        authority.substringAfterLast('@') != "$packageName.fileprovider"
