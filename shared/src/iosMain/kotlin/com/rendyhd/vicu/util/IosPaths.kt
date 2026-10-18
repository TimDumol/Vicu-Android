package com.rendyhd.vicu.util

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.numberWithBool

/**
 * Where the iOS app keeps its files. The database and preferences are in Application Support (the
 * app's own, not the App Group: the extensions never read them, and an app that is suspended while
 * it holds an SQLite lock in a shared container is killed). The App Group holds only what the
 * widget and share extensions exchange with the app.
 */
object IosPaths {

    /** Application Support, created when missing. */
    val applicationSupport: String by lazy { directory(NSApplicationSupportDirectory) }

    /** Caches: files the system may delete when space runs low. */
    val caches: String by lazy { directory(NSCachesDirectory) }

    /** The App Group container named in Info.plist (VicuAppGroup), or null when the build has none. */
    val appGroup: String? by lazy {
        val id = NSBundle.mainBundle.objectForInfoDictionaryKey("VicuAppGroup") as? String
        id?.takeIf { it.isNotBlank() && !it.startsWith("$(") }
            ?.let { NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(it)?.path }
    }

    /** The URL scheme the build registers (VicuUrlScheme in Info.plist). */
    val urlScheme: String by lazy {
        (NSBundle.mainBundle.objectForInfoDictionaryKey("VicuUrlScheme") as? String)
            ?.takeIf { it.isNotBlank() && !it.startsWith("$(") } ?: "vicu"
    }

    /** [path] inside [parent], created as a directory when missing. */
    fun subdirectory(parent: String, path: String): String {
        val full = "$parent/$path"
        NSFileManager.defaultManager.createDirectoryAtPath(full, withIntermediateDirectories = true, attributes = null, error = null)
        return full
    }

    /** Keeps [path] out of iCloud and device backups (it holds account data or a cache). */
    @OptIn(BetaInteropApi::class)
    fun excludeFromBackup(path: String) {
        NSURL.fileURLWithPath(path).setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    private fun directory(kind: ULong): String {
        val url = NSFileManager.defaultManager.URLForDirectory(kind, NSUserDomainMask, null, true, null)
        return requireNotNull(url?.path) { "no directory for $kind" }
    }
}
