package com.rendyhd.vicu.util

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosPathsTest {
    private val parent = NSTemporaryDirectory() + "paths-test-" + NSUUID().UUIDString

    private fun excluded(path: String): Boolean {
        val values = NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
        return when (val value = values?.get(NSURLIsExcludedFromBackupKey)) {
            is NSNumber -> value.boolValue
            is Boolean -> value
            else -> false
        }
    }

    @AfterTest
    fun cleanUp() {
        fun delete(path: Path) {
            if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) SystemFileSystem.list(path).forEach(::delete)
            SystemFileSystem.delete(path, mustExist = false)
        }
        delete(Path(parent))
    }

    @Test
    fun anExcludedFolderIsMadeAndKeptOutOfBackups() {
        SystemFileSystem.createDirectories(Path(parent))
        assertFalse(excluded(parent))

        val folder = IosPaths.excludedSubdirectory(parent, "widgets")

        assertTrue(SystemFileSystem.metadataOrNull(Path(folder))?.isDirectory == true)
        assertTrue(excluded(folder))
        // Asked again with files in it (written whole and moved in, so not marked themselves):
        // the same folder, still marked.
        SystemFileSystem.sink(Path("$folder/catalog.json")).buffered().use { it.writeString("{}") }
        assertEquals(folder, IosPaths.excludedSubdirectory(parent, "widgets"))
        assertTrue(excluded(folder))
    }
}
