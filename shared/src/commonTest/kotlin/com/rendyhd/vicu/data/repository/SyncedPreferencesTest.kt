package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.auth.InMemoryTokenStorage
import com.rendyhd.vicu.auth.authHarness
import com.rendyhd.vicu.auth.authTestJson
import com.rendyhd.vicu.auth.authTestJsonHeaders
import com.rendyhd.vicu.data.local.CarrierIdStore
import com.rendyhd.vicu.data.local.SyncedPrefsStore
import com.rendyhd.vicu.data.remote.api.VikunjaApiService
import com.rendyhd.vicu.data.sync.CarrierFinder
import com.rendyhd.vicu.data.sync.Req
import com.rendyhd.vicu.data.sync.SyncedDocumentStatus
import com.rendyhd.vicu.data.sync.TaskListServer
import com.rendyhd.vicu.domain.model.CustomListRevision
import com.rendyhd.vicu.domain.model.SyncedPrefEntry
import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import com.rendyhd.vicu.domain.model.SyncedPrefsDocument
import com.rendyhd.vicu.util.AppMessages
import com.rendyhd.vicu.util.MutableTimeSource
import com.rendyhd.vicu.util.SyncedPrefsEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The synced settings and the Inbox (docs/cross-app-semantics-v1.md, section 11). */
class SyncedPreferencesTest {

    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private class Rig(
        val prefs: SyncedPreferences,
        val tasks: TaskListServer,
        val storage: InMemoryTokenStorage,
        val hooks: RecordingRepositoryHooks,
    ) {
        fun creates() = tasks.requests.filter { it.method == "POST" }
        fun writes() = tasks.requests.filter { it.method == "PATCH" }
        fun carrier(): SyncedPrefsDocument? =
            tasks.tasks.values.firstOrNull { SyncedPrefsEnvelope.hasMarker(it.description) }
                ?.let { SyncedPrefsEnvelope.parse(it.description, authTestJson).document }
    }

    private fun carrierOf(vararg entries: Pair<String, SyncedPrefEntry>) =
        SyncedPrefsEnvelope.encode(SyncedPrefsDocument(entries = entries.toMap()), authTestJson)

    private fun entry(value: Long?, wall: Long = 100, device: String = "desk") =
        SyncedPrefEntry(value?.let { JsonPrimitive(it) }, CustomListRevision(wall, 0, device))

    private suspend fun TestScope.rig(tasks: TaskListServer = TaskListServer(), inbox: Long? = 5): Rig {
        var nextId = 950L
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                val id = path.removePrefix("/tasks/").toLongOrNull()
                when {
                    request.method.value == "POST" && path.startsWith("/projects/") -> {
                        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        val created = tasks.put(nextId++, title = body.getValue("title").jsonPrimitive.content, done = true,
                            description = body.getValue("description").jsonPrimitive.content)
                        tasks.requests += Req("POST", path, emptyMap())
                        respond("""{"id":${created.id},"done":true,"project_id":5,"created_by":{"id":1}}""", HttpStatusCode.Created, authTestJsonHeaders)
                    }
                    request.method.value == "PATCH" && id != null -> {
                        val patch = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                        val task = tasks.tasks.getValue(id)
                        patch["description"]?.let { task.description = it.jsonPrimitive.content }
                        tasks.requests += Req("PATCH", path, emptyMap())
                        respond("""{"id":$id,"done":true,"project_id":5,"created_by":{"id":1}}""", HttpStatusCode.OK, authTestJsonHeaders)
                    }
                    else -> tasks.handle(this, request)
                }
            },
        ) { install(ContentNegotiation) { json(authTestJson) } }
        val api = VikunjaApiService(client, authTestJson)
        val storage = InMemoryTokenStorage(initialUserId = 1).also {
            it.storeVikunjaUrl("https://vikunja.example")
            if (inbox != null) it.storeInboxProjectId(inbox)
        }
        val time = MutableTimeSource(now)
        val hooks = RecordingRepositoryHooks()
        val prefs = SyncedPreferences(
            store = SyncedPrefsStore(InMemoryPreferencesDataStore()),
            api = api,
            carrierFinder = CarrierFinder(api, CarrierIdStore(InMemoryPreferencesDataStore()), time),
            authManager = authHarness(backgroundScope, storage) { respond("", HttpStatusCode.NotFound) }.manager,
            platformHooks = hooks,
            json = authTestJson,
            messages = AppMessages(),
            time = time,
        )
        return Rig(prefs, tasks, storage, hooks)
    }

    @Test
    fun `a device that changed nothing and has no Inbox before the update creates no carrier`() = runTest {
        val rig = rig(inbox = null)

        assertEquals(SyncedDocumentStatus.Idle, rig.prefs.sync())

        assertEquals(emptyList(), rig.creates())
        assertNull(rig.carrier())
    }

    @Test
    fun `the first sync after the update moves this device's Inbox into a new carrier`() = runTest {
        val rig = rig(inbox = 5)

        rig.prefs.sync()

        assertEquals(1, rig.creates().size)
        assertEquals(JsonPrimitive(5L), rig.carrier()?.entries?.get(SyncedPrefKeys.INBOX_PROJECT_ID)?.value)
        assertEquals(5L, rig.prefs.syncedInboxProjectId())
    }

    @Test
    fun `an Inbox another device chose wins over this device's and moves it`() = runTest {
        val tasks = TaskListServer().apply {
            put(800, done = true, description = carrierOf(SyncedPrefKeys.INBOX_PROJECT_ID to entry(9)))
        }
        val rig = rig(tasks, inbox = 5)

        rig.prefs.sync()

        assertEquals(9L, rig.storage.getInboxProjectId(), "this device follows")
        assertEquals(JsonPrimitive(9L), rig.carrier()?.entries?.get(SyncedPrefKeys.INBOX_PROJECT_ID)?.value, "and writes nothing over it")
        assertEquals(emptyList(), rig.writes())
    }

    @Test
    fun `a device in setup is not moved but can read the synced Inbox`() = runTest {
        val tasks = TaskListServer().apply {
            put(800, done = true, description = carrierOf(SyncedPrefKeys.INBOX_PROJECT_ID to entry(9)))
        }
        val rig = rig(tasks, inbox = null)

        rig.prefs.sync()

        assertNull(rig.storage.getInboxProjectId())
        assertEquals(9L, rig.prefs.syncedInboxProjectId())
    }

    @Test
    fun `choosing an Inbox sets it here and writes it for the other devices`() = runTest {
        val tasks = TaskListServer().apply {
            put(800, done = true, description = carrierOf(SyncedPrefKeys.INBOX_PROJECT_ID to entry(9)))
        }
        val rig = rig(tasks, inbox = 9)
        rig.prefs.sync()

        rig.prefs.chooseInbox(12)
        assertTrue(rig.prefs.hasUnsyncedChanges.first())
        rig.prefs.sync()

        assertEquals(12L, rig.storage.getInboxProjectId())
        assertEquals(JsonPrimitive(12L), rig.carrier()?.entries?.get(SyncedPrefKeys.INBOX_PROJECT_ID)?.value)
        assertFalse(rig.prefs.hasUnsyncedChanges.first())
    }

    @Test
    fun `a reset writes null so it reaches the other devices, and a default is never written`() = runTest {
        val rig = rig(inbox = null)

        rig.prefs.set(SyncedPrefKeys.TASK_ORDER, null)
        rig.prefs.sync()
        assertNull(rig.carrier(), "nothing chosen, nothing written")

        rig.prefs.set(SyncedPrefKeys.TASK_ORDER, JsonPrimitive("manual"))
        rig.storage.storeInboxProjectId(5)
        rig.prefs.sync()
        rig.prefs.set(SyncedPrefKeys.TASK_ORDER, null)
        rig.prefs.sync()

        val stored = rig.carrier()?.entries?.get(SyncedPrefKeys.TASK_ORDER)
        assertTrue(stored != null && stored.value == null, stored.toString())
        assertEquals(mapOf(SyncedPrefKeys.TASK_ORDER to null), rig.prefs.entries.first().filterKeys { it == SyncedPrefKeys.TASK_ORDER })
    }
}
