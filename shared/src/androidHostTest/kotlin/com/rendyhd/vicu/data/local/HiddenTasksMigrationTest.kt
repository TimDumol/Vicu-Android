package com.rendyhd.vicu.data.local

import com.rendyhd.vicu.util.HiddenTasks
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs [MIGRATION_4_5] against a real SQLite (sqlite-jdbc): version 5 recomputes `isMetadata` with
 * the rule every hidden task follows (docs/cross-app-semantics-v1.md, section 10.1), so a
 * preferences carrier or a kind added later is hidden in rows cached before the update.
 */
class HiddenTasksMigrationTest {

    private fun Connection.insertTask(id: Long, description: String, isMetadata: Int) {
        prepareStatement(
            "INSERT INTO tasks (id, title, description, done, doneAt, dueDate, priority, projectId, repeatAfter, repeatMode, " +
                "startDate, endDate, hexColor, percentDone, task_index, position, kanbanPosition, bucketId, created, updated, " +
                "createdById, createdByUsername, labelsJson, remindersJson, attachmentsJson, relatedTasksJson, isFavorite, isMetadata) " +
                "VALUES (?, 'Task', ?, 1, '', '', 0, 7, 0, 0, '', '', '', 0.0, 0, 0.0, 0.0, 0, '', '', 0, '', '[]', '[]', '[]', '{}', 0, ?)",
        ).use {
            it.setLong(1, id)
            it.setString(2, description)
            it.setInt(3, isMetadata)
            it.executeUpdate()
        }
    }

    private fun Connection.flags(): Map<Long, Int> =
        createStatement().use { s ->
            s.executeQuery("SELECT id, isMetadata FROM tasks ORDER BY id").use { rs ->
                buildMap { while (rs.next()) put(rs.getLong(1), rs.getInt(2)) }
            }
        }

    private fun migrate(connection: Connection) {
        MIGRATION_4_5.migrate(JdbcSqliteConnection(connection))
    }

    /** Description and the flag version 4 stored for it. */
    private val samples = listOf(
        "<!-- vicu-routine:v1:eyJ4IjoxfQ -->" to 1,
        "<!-- vicu-custom-lists:v1:e30 -->" to 1,
        "<!-- vicu-prefs:v1:e30 -->" to 0,
        "<p>Notes</p><!--vicu-someday-kind:v3:e30-->" to 0,
        "<!-- VICU-ROUTINE:v1:abc -->" to 1,
        "<p>see vicu-prefs: in the docs</p>" to 0,
        "<p>Buy milk</p>" to 0,
        "" to 0,
    )

    @Test
    fun `it migrates from 4 to 5`() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    @Test
    fun `every flag equals the rule that sets it on every later write`() {
        val connection = ExportedSchema.database(4)
        samples.forEachIndexed { index, (description, flag) -> connection.insertTask(index + 1L, description, flag) }

        migrate(connection)

        val flags = connection.flags()
        samples.forEachIndexed { index, (description, _) ->
            assertEquals(HiddenTasks.isHidden(description), flags.getValue(index + 1L) == 1, description)
        }
        assertEquals(1, flags.getValue(3L), "the preferences carrier is hidden now")
        assertEquals(0, flags.getValue(5L), "the upper-case marker the version 3 backfill flagged is not")
    }

    @Test
    fun `the tables are those of the exported version 5 schema`() {
        val migrated = ExportedSchema.database(4).also { migrate(it) }
        val expected = ExportedSchema.database(5)
        for (entity in ExportedSchema.entities(5)) {
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            assertEquals(expected.tableSql(table), migrated.tableSql(table), table)
        }
    }

    private fun Connection.tableSql(table: String): String? =
        prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?").use { s ->
            s.setString(1, table)
            s.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
}
