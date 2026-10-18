@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.rendyhd.vicu.data.sync

import com.rendyhd.vicu.auth.InMemoryTokenStorage
import com.rendyhd.vicu.auth.authHarness
import com.rendyhd.vicu.auth.authTestJson
import com.rendyhd.vicu.data.local.CarrierIdStore
import com.rendyhd.vicu.data.local.CustomListStore
import com.rendyhd.vicu.data.remote.api.VikunjaApiService
import com.rendyhd.vicu.data.repository.CustomListRepositoryImpl
import com.rendyhd.vicu.data.repository.InMemoryPreferencesDataStore
import com.rendyhd.vicu.data.repository.RecordingRepositoryHooks
import com.rendyhd.vicu.domain.model.CustomListRevision
import com.rendyhd.vicu.domain.model.CustomListSyncDocumentV1
import com.rendyhd.vicu.domain.model.CustomListSyncOrder
import com.rendyhd.vicu.domain.model.CustomListSyncRecord
import com.rendyhd.vicu.domain.model.CustomListSyncStatus
import com.rendyhd.vicu.domain.model.CustomListWire
import com.rendyhd.vicu.domain.model.CustomListWireFilter
import com.rendyhd.vicu.util.CustomListEnvelope
import com.rendyhd.vicu.util.MutableTimeSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The synced-document engine, through the custom lists (docs/cross-app-semantics-v1.md, section
 * 10): tombstones older than 90 days are dropped before a write, a duplicate carrier is deleted
 * once the kept one holds all of it, and nothing of another user or of a newer version is touched.
 */
class SyncedDocumentSyncTest {

    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val day = 24L * 60 * 60 * 1000

    private class Rig(val repository: CustomListRepositoryImpl, val tasks: TaskListServer, val ids: CarrierIdStore) {
        fun writes() = tasks.requests.filter { it.method == "PATCH" || it.method == "POST" }
        fun deletes() = tasks.requests.filter { it.method == "DELETE" }.map { it.path.removePrefix("/tasks/").toLong() }
    }

    private fun wire(id: String) = CustomListWire(id = id, name = "List $id", filter = CustomListWireFilter())

    private fun rev(wallMs: Long, device: String = "desk") = CustomListRevision(wallMs, 0, device)

    private fun document(vararg records: Pair<String, CustomListSyncRecord>, orderAt: Long = 1L) = CustomListSyncDocumentV1(
        lists = records.toMap(),
        order = CustomListSyncOrder(records.filter { it.second.value != null }.map { it.first }, rev(orderAt)),
    )

    private fun carrier(document: CustomListSyncDocumentV1) = CustomListEnvelope.encode(document, authTestJson)

    private fun decode(description: String): CustomListSyncDocumentV1 = CustomListEnvelope.parse(description, authTestJson).document!!

    private suspend fun TestScope.rig(tasks: TaskListServer): Rig {
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val id = path.removePrefix("/tasks/").toLongOrNull()
                when {
                    request.method.value == "PATCH" && id != null -> {
                        val patch = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        val task = tasks.tasks.getValue(id)
                        patch["description"]?.let { task.description = it.jsonPrimitive.content }
                        patch["done"]?.let { task.done = it.jsonPrimitive.content == "true" }
                        tasks.requests += Req("PATCH", path, emptyMap())
                        respond("""{"id":$id,"done":true,"project_id":5,"created_by":{"id":${task.createdBy}}}""", HttpStatusCode.OK, com.rendyhd.vicu.auth.authTestJsonHeaders)
                    }
                    request.method.value == "DELETE" && id != null -> {
                        tasks.remove(id)
                        tasks.requests += Req("DELETE", path, emptyMap())
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> tasks.handle(this, request)
                }
            },
        ) { install(ContentNegotiation) { json(authTestJson) } }
        val api = VikunjaApiService(client, authTestJson)
        val storage = InMemoryTokenStorage(initialUserId = 1).also {
            it.storeVikunjaUrl("https://vikunja.example")
            it.storeInboxProjectId(5)
        }
        val ids = CarrierIdStore(InMemoryPreferencesDataStore())
        val time = MutableTimeSource(now)
        val repository = CustomListRepositoryImpl(
            store = CustomListStore(InMemoryPreferencesDataStore()),
            api = api,
            authManager = authHarness(backgroundScope, storage) { respond("", HttpStatusCode.NotFound) }.manager,
            platformHooks = RecordingRepositoryHooks(),
            json = authTestJson,
            carrierFinder = CarrierFinder(api, ids, time),
            time = time,
        )
        return Rig(repository, tasks, ids)
    }

    @Test
    fun `a tombstone older than 90 days is dropped once and the carrier is not rewritten after`() = runTest {
        val tasks = TaskListServer()
        val old = now.toEpochMilliseconds() - 91 * day
        tasks.put(900, done = true, description = carrier(document(
            "a" to CustomListSyncRecord(null, rev(old)),
            "b" to CustomListSyncRecord(wire("b"), rev(old)),
        )))
        val rig = rig(tasks)

        assertEquals(CustomListSyncStatus.Idle, rig.repository.sync())
        assertEquals(setOf("b"), decode(tasks.tasks.getValue(900).description).lists.keys)
        assertEquals(1, rig.writes().size)

        rig.tasks.requests.clear()
        assertEquals(CustomListSyncStatus.Idle, rig.repository.sync())
        assertEquals(emptyList(), rig.writes(), "both apps prune the same way, so nothing flips back")
    }

    @Test
    fun `a recent tombstone is kept`() = runTest {
        val tasks = TaskListServer()
        val recent = now.toEpochMilliseconds() - 89 * day
        tasks.put(900, done = true, description = carrier(document(
            "a" to CustomListSyncRecord(null, rev(recent)),
            "b" to CustomListSyncRecord(wire("b"), rev(recent)),
        )))
        val rig = rig(tasks)

        rig.repository.sync()

        assertEquals(setOf("a", "b"), decode(tasks.tasks.getValue(900).description).lists.keys)
        assertEquals(emptyList(), rig.writes())
    }

    @Test
    fun `a duplicate carrier is merged into the lowest id and then deleted`() = runTest {
        val tasks = TaskListServer()
        tasks.put(900, done = true, description = carrier(document("a" to CustomListSyncRecord(wire("a"), rev(10)))))
        tasks.put(901, done = true, description = carrier(document("b" to CustomListSyncRecord(wire("b"), rev(20)))))
        val rig = rig(tasks)

        assertEquals(CustomListSyncStatus.Idle, rig.repository.sync())

        assertEquals(setOf("a", "b"), decode(tasks.tasks.getValue(900).description).lists.keys)
        assertEquals(listOf(901L), rig.deletes())
        assertEquals(listOf(900L), rig.ids.get("https://vikunja.example", "custom-lists").ids)
    }

    @Test
    fun `another user's carrier is never read - merged or deleted`() = runTest {
        val tasks = TaskListServer()
        tasks.put(900, done = true, description = carrier(document("a" to CustomListSyncRecord(wire("a"), rev(10)))))
        tasks.put(899, done = true, createdBy = 2, description = carrier(document("theirs" to CustomListSyncRecord(wire("theirs"), rev(20)))))
        val rig = rig(tasks)

        rig.repository.sync()

        assertEquals(setOf("a"), decode(tasks.tasks.getValue(900).description).lists.keys)
        assertEquals(emptyList(), rig.deletes())
        assertTrue(rig.tasks.requests.none { it.path == "/tasks/899" })
    }

    @Test
    fun `a malformed extra carrier is reported and kept`() = runTest {
        val tasks = TaskListServer()
        tasks.put(900, done = true, description = carrier(document("a" to CustomListSyncRecord(wire("a"), rev(10)))))
        tasks.put(901, done = true, description = "<!-- vicu-custom-lists:v1:not-json -->")
        val rig = rig(tasks)

        val status = rig.repository.sync()

        assertTrue(status is CustomListSyncStatus.Error, status.toString())
        assertEquals(emptyList(), rig.deletes())
    }

    @Test
    fun `a v1 marker holding a newer document is update required and never written`() = runTest {
        val tasks = TaskListServer()
        val newer = """{"version":2,"lists":{},"order":{"ids":[],"revision":{"wall_time_ms":1,"counter":0,"device_id":"x"}}}"""
        tasks.put(900, done = true, description = "<!-- vicu-custom-lists:v1:${Base64.UrlSafe.encode(newer.encodeToByteArray()).trimEnd('=')} -->")
        val rig = rig(tasks)

        val status = rig.repository.sync()

        assertTrue(status is CustomListSyncStatus.UpdateRequired, status.toString())
        assertEquals(emptyList(), rig.writes())
        assertEquals(emptyList(), rig.deletes())
    }

    @Test
    fun `unknown fields at the document level survive a write`() = runTest {
        val tasks = TaskListServer()
        val withExtra = """{"version":1,"future_doc":"kept","lists":{"a":{"value":{"id":"a","name":"A","filter":{}},""" +
            """"revision":{"wall_time_ms":10,"counter":0,"device_id":"desk"},"future_record":1}},""" +
            """"order":{"ids":["a"],"revision":{"wall_time_ms":10,"counter":0,"device_id":"desk"},"future_order":true}}"""
        tasks.put(900, done = true, description = "<!-- vicu-custom-lists:v1:${Base64.UrlSafe.encode(withExtra.encodeToByteArray()).trimEnd('=')} -->")
        tasks.put(901, done = true, description = carrier(document("b" to CustomListSyncRecord(wire("b"), rev(20)))))
        val rig = rig(tasks)

        rig.repository.sync()

        val written = tasks.tasks.getValue(900).description
        val encoded = Regex("v1:([A-Za-z0-9_-]+)").find(written)!!.groupValues[1]
        val raw = Base64.UrlSafe.decode(encoded + "=".repeat((4 - encoded.length % 4) % 4)).decodeToString()
        assertTrue("\"future_doc\":\"kept\"" in raw, raw)
        assertTrue("\"future_record\":1" in raw, raw)
        assertTrue("\"future_order\":true" in raw, raw)
        assertTrue("__unknown__" !in raw, raw)
    }
}
