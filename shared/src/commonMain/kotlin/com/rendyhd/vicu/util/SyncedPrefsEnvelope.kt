@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.CustomListRevision
import com.rendyhd.vicu.domain.model.SyncedPrefEntry
import com.rendyhd.vicu.domain.model.SyncedPrefsDocument
import com.rendyhd.vicu.domain.model.SyncedPrefsDocumentSerializer
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The preferences carrier, `<!-- vicu-prefs:v1:<base64url JSON> -->` (docs/cross-app-semantics-v1.md,
 * section 11): its format, merge and the typed reads of the registry's keys.
 */
object SyncedPrefsEnvelope {
    const val CURRENT_VERSION = 1
    const val CARRIER_TITLE = "Vicu preferences (sync metadata — do not delete)"
    const val SEARCH = "vicu-prefs"
    private const val MAX_DECODED_BYTES = 512 * 1024
    private val markerRegex = Regex("""<!--\s*vicu-prefs:v(\d+):([A-Za-z0-9_-]+={0,2})\s*-->""")
    private val anyMarkerRegex = Regex("""<!--\s*vicu-prefs:[\s\S]*?-->""")

    data class Parsed(val document: SyncedPrefsDocument? = null, val error: String? = null, val version: Int? = null)

    fun hasMarker(description: String?): Boolean =
        !description.isNullOrEmpty() && anyMarkerRegex.containsMatchIn(description)

    fun parse(description: String?, json: Json): Parsed {
        val any = description?.let { anyMarkerRegex.find(it) } ?: return Parsed(error = "No preferences metadata")
        val marker = markerRegex.matchEntire(any.value) ?: return Parsed(error = "Malformed preferences metadata")
        val version = marker.groupValues[1].toIntOrNull() ?: return Parsed(error = "Invalid preferences version")
        if (version != CURRENT_VERSION) return Parsed(error = "Preferences metadata version $version is not supported", version = version)
        return runCatching {
            val encoded = marker.groupValues[2]
            val bytes = Base64.UrlSafe.decode(encoded + "=".repeat((4 - encoded.length % 4) % 4))
            require(bytes.size <= MAX_DECODED_BYTES) { "Preferences metadata is too large" }
            val document = json.decodeFromString(SyncedPrefsDocumentSerializer, bytes.decodeToString())
            if (document.version > CURRENT_VERSION) {
                return Parsed(error = "Preferences metadata version ${document.version} is not supported", version = document.version)
            }
            Parsed(normalize(document), version = version)
        }.getOrElse { Parsed(error = it.message ?: "Cannot decode preferences metadata", version = version) }
    }

    fun encode(document: SyncedPrefsDocument, json: Json): String {
        val bytes = json.encodeToString(SyncedPrefsDocumentSerializer, normalize(document)).encodeToByteArray()
        require(bytes.size <= MAX_DECODED_BYTES) { "Preferences metadata is too large" }
        return "<!-- vicu-prefs:v$CURRENT_VERSION:${Base64.UrlSafe.encode(bytes).trimEnd('=')} -->"
    }

    fun normalize(document: SyncedPrefsDocument): SyncedPrefsDocument {
        require(document.version == CURRENT_VERSION) { "Preferences payload version mismatch" }
        document.entries.forEach { (key, entry) ->
            require(key.isNotBlank() && entry.revision.deviceId.isNotBlank()) { "Preference $key is invalid" }
        }
        // One order for the keys, so two copies with the same entries encode the same.
        return document.copy(entries = document.entries.toList().sortedBy { it.first }.toMap())
    }

    /**
     * Both copies in one: the union of keys, the newer revision per key, and on equal revisions the
     * [server]'s entry. Top-level fields this version does not know: both sides', the server's wins.
     */
    fun merge(local: SyncedPrefsDocument, server: SyncedPrefsDocument): SyncedPrefsDocument {
        val entries = (local.entries.keys + server.entries.keys).associateWith { key ->
            val a = local.entries[key]
            val b = server.entries[key]
            when {
                a == null -> b!!
                b == null -> a
                CustomListEnvelope.compareRevision(a.revision, b.revision) > 0 -> a
                else -> b
            }
        }
        return normalize(SyncedPrefsDocument(entries = entries, unknown = JsonObject(local.unknown + server.unknown)))
    }

    /** Drops resets (null entries) older than 90 days (section 10.6). */
    fun pruneTombstones(document: SyncedPrefsDocument, nowMs: Long): SyncedPrefsDocument {
        val kept = document.entries.filterValues { it.value != null || !HiddenTasks.isExpiredTombstone(it.revision.wallTimeMs, nowMs) }
        return if (kept.size == document.entries.size) document else document.copy(entries = kept)
    }

    /** A revision newer than every one in [document] (the custom lists' rule). */
    fun nextRevision(document: SyncedPrefsDocument, deviceId: String, nowMs: Long): CustomListRevision {
        val revisions = document.entries.values.map { it.revision }
        val maxWall = revisions.maxOfOrNull { it.wallTimeMs } ?: 0L
        val wall = maxOf(nowMs, maxWall)
        val counter = if (nowMs > maxWall) 0 else revisions.filter { it.wallTimeMs == wall }.maxOfOrNull { it.counter }?.plus(1) ?: 0
        return CustomListRevision(wall, counter, deviceId)
    }

    /** [document] with [key] set to [value] (null: back to the default) by [deviceId] at [nowMs]. */
    fun set(document: SyncedPrefsDocument, key: String, value: JsonElement?, deviceId: String, nowMs: Long): SyncedPrefsDocument {
        val previous = document.entries[key]
        val revision = nextRevision(document, deviceId, nowMs)
        val entry = previous?.copy(value = value, revision = revision) ?: SyncedPrefEntry(value, revision)
        return normalize(document.copy(entries = document.entries + (key to entry)))
    }

    /**
     * Whether a device writes the value it kept [local]ly before a setting synced (section 11.5):
     * only a value that is not the [default], and only when the carrier has no entry for the key
     * yet (a null entry is an entry).
     */
    fun shouldMove(local: JsonElement?, default: JsonElement?, carrierHasEntry: Boolean): Boolean =
        local != null && local != default && !carrierHasEntry

    /** The Inbox project id an entry holds: a whole number above 0, anything else is no Inbox. */
    fun projectId(value: JsonElement?): Long? {
        val primitive = value as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull?.takeIf { it > 0L }
    }
}
