package com.rendyhd.vicu.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadUriPolicyTest {
    @Test
    fun rejectsPrivateFileUrisAndSelfProviderIncludingUserPrefixes() {
        val app = "com.rendyhd.vicu"
        assertFalse(isExternalUploadUri("file", null, app))
        assertFalse(isExternalUploadUri("https", "example.com", app))
        assertFalse(isExternalUploadUri("content", null, app))
        assertFalse(isExternalUploadUri("content", "$app.fileprovider", app))
        assertFalse(isExternalUploadUri("content", "0@$app.fileprovider", app))
        assertTrue(isExternalUploadUri("content", "com.android.providers.media.documents", app))
        assertTrue(isExternalUploadUri("content", "another.app.fileprovider", app))
    }
}
