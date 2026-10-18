@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import kotlin.io.encoding.Base64
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Routines written by a newer app (docs/cross-app-semantics-v1.md, sections 10.7 and 10.8): the
 * fields this version does not know survive a round trip at every level, and a carrier this
 * version cannot read is never written over.
 */
class RoutineForwardCompatTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val key = "r1:2026-10-06:s1"

    private fun payloadJson(version: Int = 1, updatedAt: String = "2026-10-06T08:00:00.000Z", status: String = "COMPLETED") = """
        {"version":$version,"future_top":{"a":1},
         "definition":{"id":"r1","name":"Water plants","kind":"CHORE",
           "schedule":{"type":"calendar","weekdays":[],"weekInterval":1,"anchorDate":"2026-10-01"},
           "slots":[{"id":"s1","label":"Morning","period":"MORNING","future_slot":"x"}],
           "activeFrom":"2026-10-01","createdAt":"2026-10-01T08:00:00.000Z","updatedAt":"$updatedAt","updatedBy":"desk",
           "future_definition":[1,2]},
         "occurrences":{"$key":{"key":"$key","routineId":"r1","slotId":"s1","scheduledDate":"2026-10-06",
           "scheduledMinutes":480,"timeZoneId":"Europe/Amsterdam","status":"$status",
           "modifiedAt":"2026-10-06T08:00:00.000Z","modifiedBy":"desk","future_occurrence":true}},
         "prunedBefore":""}
    """.trimIndent()

    private fun carrier(payload: String) =
        "<!-- vicu-routine:v1:${Base64.UrlSafe.encode(payload.encodeToByteArray()).trimEnd('=')} -->"

    private fun decoded(description: String): JsonObject {
        val encoded = Regex("vicu-routine:v1:([A-Za-z0-9_-]+)").find(description)!!.groupValues[1]
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Json.parseToJsonElement(Base64.UrlSafe.decode(padded).decodeToString()).jsonObject
    }

    @Test
    fun `unknown fields survive a round trip at every level`() {
        val payload = assertNotNull(RoutineEnvelope.parse(carrier(payloadJson()), json).payload)

        val written = decoded(RoutineEnvelope.encode(payload, json))

        assertEquals("""{"a":1}""", written["future_top"].toString())
        val definition = written.getValue("definition").jsonObject
        assertEquals("[1,2]", definition["future_definition"].toString())
        assertEquals("\"x\"", definition["slots"].toString().let { Regex("\"future_slot\":(\"x\")").find(it)!!.groupValues[1] })
        val occurrence = written.getValue("occurrences").jsonObject.getValue(key).jsonObject
        assertEquals(JsonPrimitive(true), occurrence["future_occurrence"])
        assertTrue("__unknown__" !in written.toString(), "the holder never reaches the wire")
    }

    @Test
    fun `the winning definition and records carry their own unknown fields - the server wins at the top`() {
        val local = assertNotNull(RoutineEnvelope.parse(carrier(payloadJson(updatedAt = "2026-10-06T09:00:00.000Z")), json).payload)
            .let { it.copy(unknown = JsonObject(mapOf("future_top" to JsonPrimitive("local"), "local_only" to JsonPrimitive(1)))) }
        val remote = assertNotNull(RoutineEnvelope.parse(carrier(payloadJson()), json).payload)

        val merged = RoutineEnvelope.mergePayload(local, remote)

        assertEquals("""{"a":1}""", merged.unknown["future_top"].toString())
        assertEquals(JsonPrimitive(1), merged.unknown["local_only"])
        assertEquals("[1,2]", merged.definition.unknown["future_definition"].toString())
    }

    @Test
    fun `logging an occurrence again keeps the fields a newer app put on it`() {
        val payload = assertNotNull(RoutineEnvelope.parse(carrier(payloadJson()), json).payload)
        val routine = Routine(taskId = 5, payload = payload)

        val record = RoutineScheduleEngine.recordFor(
            routine = routine,
            date = LocalDate.parse("2026-10-06"),
            slot = payload.definition.slots.single(),
            status = OccurrenceStatus.SKIPPED,
            nowIso = "2026-10-06T10:00:00.000Z",
            deviceId = "phone",
            timeZone = TimeZone.of("Europe/Amsterdam"),
        )

        assertEquals(JsonPrimitive(true), record.unknown["future_occurrence"])
    }

    @Test
    fun `a newer payload version is update required - not damage`() {
        val parsed = RoutineEnvelope.parse(carrier(payloadJson(version = 2)), json)
        assertNull(parsed.payload)
        assertTrue(parsed.isNewerVersion)

        val newerMarker = RoutineEnvelope.parse("<!-- vicu-routine:v3:e30 -->", json)
        assertTrue(newerMarker.isNewerVersion)
    }

    @Test
    fun `a write over a carrier this version cannot read is refused`() {
        val local = RoutineEnvelope.parse(carrier(payloadJson()), json)
        val newer = RoutineEnvelope.parse(carrier(payloadJson(version = 2)), json)
        val damaged = RoutineEnvelope.parse("<!-- vicu-routine:v1:not-json -->", json)
        val plain = RoutineEnvelope.parse("<p>no routine any more</p>", json)

        val updateRequired = assertFailsWith<RoutineEnvelope.UnreadableCarrierException> { RoutineEnvelope.mergeForWrite(local, newer) }
        assertTrue("newer version" in updateRequired.message.orEmpty())
        assertFailsWith<RoutineEnvelope.UnreadableCarrierException> { RoutineEnvelope.mergeForWrite(local, damaged) }
        assertFailsWith<RoutineEnvelope.UnreadableCarrierException> { RoutineEnvelope.mergeForWrite(damaged, local) }
        assertNotNull(RoutineEnvelope.mergeForWrite(local, local))
        assertNull(RoutineEnvelope.mergeForWrite(local, plain))
    }
}
