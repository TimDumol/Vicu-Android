package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.data.remote.api.VikunjaApiService
import com.rendyhd.vicu.data.remote.createApiJson
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.RoutineLoggedBy
import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.util.RoutineEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two people ticking a shared routine at the same moment, against a real Vikunja server, with the
 * app's own [RoutineCarrierWriter] (cross-app semantics 6.8). Skipped unless `VICU_LIVE_RACE` names
 * the ui-verify `.local` folder (credentials of two members of the routine's project) and
 * `VICU_LIVE_RACE_TASK` the carrier's task id; `VICU_LIVE_RACE_SERVER` defaults to the harness
 * server. It writes test occurrences into that carrier.
 */
class LiveRoutineRaceTest {

    private val json = createApiJson()

    private fun client(token: String?, server: String) = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            url("$server/api/v2/")
            if (token != null) bearerAuth(token)
        }
    }

    private suspend fun login(local: File, file: String, server: String): Pair<String, Long> {
        val credentials = Json.parseToJsonElement(File(local, file).readText()).jsonObject
        val anonymous = client(null, server)
        val token = Json.parseToJsonElement(
            anonymous.post("login") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("username", credentials.getValue("username").jsonPrimitive.content)
                        put("password", credentials.getValue("password").jsonPrimitive.content)
                        put("long_token", true)
                    }.toString(),
                )
            }.bodyAsText(),
        ).jsonObject.getValue("token").jsonPrimitive.content
        val me = Json.parseToJsonElement(client(token, server).get("user").bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
        return token to me
    }

    @Test
    fun `two people ticking at the same moment both count`() = runBlocking {
        val local = System.getenv("VICU_LIVE_RACE")?.let(::File) ?: return@runBlocking
        val taskId = System.getenv("VICU_LIVE_RACE_TASK")?.toLong() ?: return@runBlocking
        val server = System.getenv("VICU_LIVE_RACE_SERVER") ?: "http://localhost:3456"
        val people = listOf("credentials.json", "credentials-uimaya.json").map { file ->
            val (token, me) = login(local, file, server)
            Triple(VikunjaApiService(client(token, server), json), me, "live-race-$me")
        }
        val pairs = 20
        var lost = 0
        for (i in 0 until pairs) {
            val date = "2026-08-%02d".format(i + 1)
            val start = CompletableDeferred<Unit>()
            val writes = people.mapIndexed { index, (api, me, device) ->
                async(Dispatchers.IO) {
                    val read = api.getTask(taskId)
                    val parsed = RoutineEnvelope.parse(read.description, json)
                    val payload = checkNotNull(parsed.payload)
                    val slot = payload.definition.slots[index % payload.definition.slots.size]
                    val stamp = java.time.Instant.now().toString()
                    val record = RoutineOccurrenceRecord(
                        key = "${payload.definition.id}:$date:${slot.id}",
                        routineId = payload.definition.id,
                        slotId = slot.id,
                        scheduledDate = date,
                        scheduledMinutes = slot.reminderMinutes,
                        timeZoneId = "Europe/Amsterdam",
                        status = OccurrenceStatus.COMPLETED,
                        loggedAt = stamp,
                        modifiedAt = stamp,
                        modifiedBy = device,
                        loggedBy = RoutineLoggedBy(me, device),
                    )
                    val localDescription = RoutineEnvelope.upsert(
                        parsed.body,
                        payload.copy(occurrences = payload.occurrences + (record.key to record)),
                        json,
                    )
                    start.await()
                    RoutineCarrierWriter(api, json).write(taskId, localDescription)
                    record.key
                }
            }
            start.complete(Unit)
            val keys = writes.awaitAll()
            val stored = checkNotNull(RoutineEnvelope.parse(people[0].first.getTask(taskId).description, json).payload)
            lost += keys.count { it !in stored.occurrences }
        }
        println("Live race: $pairs pairs, ${2 * pairs} writes, lost $lost")
        assertEquals(0, lost)
    }
}
