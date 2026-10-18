package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.model.TaskOrder
import com.rendyhd.vicu.domain.model.TaskOrderSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `taskOrder` vectors of test-fixtures/cross-app-semantics-v1.json (contract section 12), and
 * the `syncedPrefs.read` vectors of the task order keys. The desktop runs the same file.
 */
class TaskOrderFixtureTest {

    @Serializable
    private data class FixtureTask(
        val id: Long,
        val title: String,
        val due: String? = null,
        val priority: Int,
        val position: Double = 0.0,
        val created: String? = null,
    ) {
        fun task() = Task(id = id, title = title, dueDate = due.orEmpty(), priority = priority, position = position, created = created.orEmpty())
    }

    @Serializable
    private data class SortVector(val order: String, val expect: List<Long>)

    @Serializable
    private data class ResolveVector(val name: String, val entries: JsonObject, val expect: Map<String, String>)

    @Serializable
    private data class Resolve(val parents: Map<String, Long>, val vectors: List<ResolveVector>)

    @Serializable
    private data class Block(val tasks: List<FixtureTask>, val sort: List<SortVector>, val resolve: Resolve, val drag: List<SortVector>)

    @Serializable
    private data class ReadVector(val name: String, val entries: JsonObject, val key: String, val expect: JsonElement)

    private val json = Json { ignoreUnknownKeys = true }

    private val root: JsonObject by lazy { json.parseToJsonElement(CrossAppFixture.readTestFixture("cross-app-semantics-v1.json")).jsonObject }
    private val block: Block by lazy { json.decodeFromJsonElement(Block.serializer(), root.getValue("taskOrder")) }

    private fun orderOf(key: String) = TaskOrder.fromKey(key) ?: TaskOrder.DEFAULT

    @Test
    fun `each order sorts the tasks the same way as the desktop`() {
        val tasks = block.tasks.map { it.task() }
        for (v in block.sort) {
            assertEquals(v.expect, sortProjectTasks(tasks.shuffled(kotlin.random.Random(v.order.hashCode())), orderOf(v.order)).map { it.id }, v.order)
            assertEquals(v.expect, sortProjectTasks(tasks.reversed(), orderOf(v.order)).map { it.id }, "${v.order}, from another start")
        }
    }

    @Test
    fun `a project uses its own order - else its nearest parent's - else the global one`() {
        val parentOf = block.resolve.parents.mapKeys { it.key.toLong() }
        for (v in block.resolve.vectors) {
            val settings = TaskOrderSettings.fromEntries(v.entries)
            for ((project, expected) in v.expect) {
                assertEquals(expected, settings.orderFor(project.toLong(), parentOf).key, "${v.name}: project $project")
            }
        }
    }

    @Test
    fun `only manual order drags every row and the default only the undated ones`() {
        val tasks = block.tasks.map { it.task() }
        for (v in block.drag) {
            assertEquals(v.expect.sorted(), tasks.filter { isManuallyOrdered(it, orderOf(v.order)) }.map { it.id }.sorted(), v.order)
        }
    }

    @Test
    fun `the global order reads as the default when absent - null or unknown`() {
        val reads = root.getValue("syncedPrefs").jsonObject.getValue("read").let { json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(ReadVector.serializer()), it) }
        for (v in reads.filter { it.key == SyncedPrefKeys.TASK_ORDER }) {
            assertEquals(v.expect.jsonPrimitive.content, TaskOrderSettings.fromEntries(v.entries).global.key, v.name)
        }
    }
}
