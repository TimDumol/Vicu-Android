package com.rendyhd.vicu.util

import com.rendyhd.vicu.data.remote.api.TaskDto
import com.rendyhd.vicu.data.remote.api.UserDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `hiddenTasks` vectors of test-fixtures/cross-app-semantics-v1.json (contract section 10).
 * The desktop runs the same file.
 */
class HiddenTasksFixtureTest {

    @Serializable
    private data class MarkerVector(val name: String, val title: String, val description: String? = null, val hidden: Boolean)

    @Serializable
    private data class Carrier(val id: Long, val createdBy: Long? = null)

    @Serializable
    private data class OwnershipVector(val name: String, val me: Long? = null, val carriers: List<Carrier>, val use: List<Long>, val write: Long? = null)

    @Serializable
    private data class Record(val id: String, val wallTimeMs: Long, val hasValue: Boolean)

    @Serializable
    private data class Tombstones(val nowMs: Long, val records: List<Record>, val keep: List<String>)

    @Serializable
    private data class Block(val markers: List<MarkerVector>, val ownership: List<OwnershipVector>, val tombstones: Tombstones)

    private val json = Json { ignoreUnknownKeys = true }

    private val block: Block by lazy {
        val root = json.parseToJsonElement(CrossAppFixture.readTestFixture("cross-app-semantics-v1.json")).jsonObject
        json.decodeFromJsonElement(Block.serializer(), root["hiddenTasks"] as JsonObject)
    }

    @Test
    fun `a task is hidden by its marker - whatever the kind and never by its title`() {
        for (v in block.markers) assertEquals(v.hidden, HiddenTasks.isHidden(v.description), v.name)
    }

    @Test
    fun `only the signed-in user's carriers are used - and the lowest id is written`() {
        for (v in block.ownership) {
            val tasks = v.carriers.map { TaskDto(id = it.id, createdBy = it.createdBy?.let { id -> UserDto(id = id) }) }
            val used = tasks.filter { HiddenTasks.isOwn(it, v.me) }.map { it.id }.sorted()
            assertEquals(v.use, used, v.name)
            assertEquals(v.write, used.minOrNull(), v.name)
        }
    }

    @Test
    fun `tombstones older than 90 days are dropped and values never are`() {
        val t = block.tombstones
        val kept = t.records.filter { it.hasValue || !HiddenTasks.isExpiredTombstone(it.wallTimeMs, t.nowMs) }.map { it.id }
        assertEquals(t.keep, kept)
    }
}
