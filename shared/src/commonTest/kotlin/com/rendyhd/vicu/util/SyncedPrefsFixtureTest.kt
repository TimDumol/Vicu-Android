package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import com.rendyhd.vicu.domain.model.SyncedPrefsDocumentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `syncedPrefs` vectors of test-fixtures/cross-app-semantics-v1.json (contract section 11).
 * The task order's reads are in [TaskOrderFixtureTest]. The desktop runs the same file.
 */
class SyncedPrefsFixtureTest {

    @Serializable
    private data class MergeVector(val name: String, val local: JsonObject, val server: JsonObject, val expect: JsonObject)

    @Serializable
    private data class ReadVector(val name: String, val entries: JsonObject, val key: String, val expect: JsonElement)

    @Serializable
    private data class MoveVector(val name: String, val key: String, val local: JsonElement, val carrier: JsonElement? = null, val expectWrite: Boolean)

    @Serializable
    private data class Block(val merge: List<MergeVector>, val read: List<ReadVector>, val move: List<MoveVector>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val raw: JsonObject by lazy {
        json.parseToJsonElement(CrossAppFixture.readTestFixture("cross-app-semantics-v1.json")).jsonObject.getValue("syncedPrefs").jsonObject
    }
    private val block: Block by lazy { json.decodeFromJsonElement(Block.serializer(), raw) }

    @Test
    fun `two copies merge entry by entry and keep what this version does not know`() {
        for (v in block.merge) {
            val local = json.decodeFromJsonElement(SyncedPrefsDocumentSerializer, v.local)
            val server = json.decodeFromJsonElement(SyncedPrefsDocumentSerializer, v.server)
            val merged = json.encodeToJsonElement(SyncedPrefsDocumentSerializer, SyncedPrefsEnvelope.merge(local, server))
            assertEquals(v.expect, merged, v.name)
        }
    }

    @Test
    fun `the Inbox reads only a whole project id above 0`() {
        for (v in block.read.filter { it.key == SyncedPrefKeys.INBOX_PROJECT_ID }) {
            val value = v.entries[v.key]
            val expected = if (v.expect is JsonNull) null else v.expect.jsonPrimitive.longOrNull
            assertEquals(expected, SyncedPrefsEnvelope.projectId(value), v.name)
        }
    }

    @Test
    fun `a setting kept on the device moves only when it is not the default and the carrier has no entry`() {
        // The move vectors of the "carrier" field: absent means no entry, present (null too) an entry.
        val raws = raw.getValue("move").let { it as kotlinx.serialization.json.JsonArray }
        block.move.forEachIndexed { index, v ->
            val hasEntry = "carrier" in raws[index].jsonObject
            val default = if (v.key == SyncedPrefKeys.TASK_ORDER) kotlinx.serialization.json.JsonPrimitive("due_date_first") else null
            val local = v.local.takeUnless { it is JsonNull }
            assertEquals(v.expectWrite, SyncedPrefsEnvelope.shouldMove(local, default, hasEntry), v.name)
        }
    }
}
