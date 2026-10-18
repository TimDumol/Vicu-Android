package com.rendyhd.vicu.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.rendyhd.vicu.util.HiddenTasks

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `routine_occurrence_archive` (
                `routineId` TEXT NOT NULL,
                `occurrenceKey` TEXT NOT NULL,
                `slotId` TEXT NOT NULL,
                `scheduledDate` TEXT NOT NULL,
                `scheduledMinutes` INTEGER NOT NULL,
                `timeZoneId` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `loggedAt` TEXT NOT NULL,
                `modifiedAt` TEXT NOT NULL,
                `modifiedBy` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                PRIMARY KEY(`routineId`, `occurrenceKey`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Version 3 stores whether a task is hidden sync metadata (a routine carrier or archive part, a
 * custom-list carrier) in a column of its own, so the list queries can leave those rows out in SQL
 * (A-UI-19). The statements are public so a test can run them against a real SQLite.
 *
 * The backfill uses `LIKE`: a marker comment names `vicu-routine:` or `vicu-custom-lists:` after
 * `<!--`. That is slightly looser than the regex that sets the column on every later write
 * (then `CustomListEnvelope.isAnyMetadataTask`, now [com.rendyhd.vicu.util.HiddenTasks.isHidden]): it ignores case and what sits
 * between `<!--` and the name. A row that matches here but not there is corrected the next time
 * the row is written, which the next refresh does because the stored row differs from the one
 * mapped from the server.
 */
val TASK_METADATA_MIGRATION_SQL: List<String> = listOf(
    "ALTER TABLE `tasks` ADD COLUMN `isMetadata` INTEGER NOT NULL DEFAULT 0",
    """
    UPDATE `tasks` SET `isMetadata` = 1
    WHERE `description` LIKE '%<!--%vicu-routine:%-->%'
       OR `description` LIKE '%<!--%vicu-custom-lists:%-->%'
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS `index_tasks_isMetadata` ON `tasks` (`isMetadata`)",
)

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        TASK_METADATA_MIGRATION_SQL.forEach { connection.execSQL(it) }
    }
}

/**
 * Version 4 adds collaboration (docs/cross-app-semantics-v1.md section 9): the assignees of a task,
 * your permission on a project, the members of each project, the comment signals of the tasks in
 * shared projects and the comment threads that were opened on this phone. Public so a test can run
 * the statements against a real SQLite.
 */
val COLLABORATION_MIGRATION_SQL: List<String> = listOf(
    "ALTER TABLE `tasks` ADD COLUMN `assigneesJson` TEXT NOT NULL DEFAULT '[]'",
    "ALTER TABLE `projects` ADD COLUMN `maxPermission` INTEGER NOT NULL DEFAULT -1",
    "CREATE TABLE IF NOT EXISTS `project_members` (`projectId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `username` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `userId`))",
    "CREATE TABLE IF NOT EXISTS `task_signals` (`taskId` INTEGER NOT NULL, `commentCount` INTEGER NOT NULL, `isUnread` INTEGER NOT NULL, PRIMARY KEY(`taskId`))",
    "CREATE TABLE IF NOT EXISTS `task_comments` (`id` INTEGER NOT NULL, `taskId` INTEGER NOT NULL, `authorId` INTEGER NOT NULL, `authorUsername` TEXT NOT NULL, `authorName` TEXT NOT NULL, `comment` TEXT NOT NULL, `created` TEXT NOT NULL, `updated` TEXT NOT NULL, `pending` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_task_comments_taskId` ON `task_comments` (`taskId`)",
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        COLLABORATION_MIGRATION_SQL.forEach { connection.execSQL(it) }
    }
}

/**
 * Version 5 recomputes `isMetadata` with the marker rule every hidden task follows
 * ([HiddenTasks.isHidden], docs/cross-app-semantics-v1.md section 10.1): any `<!-- vicu-<kind>:`,
 * so the preferences carrier and kinds a later version adds are hidden too. The tables do not
 * change. SQL only narrows the candidates; the rule itself runs here, so the stored flag equals the
 * one every later write sets, which a `LIKE` could only approximate.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        val changes = mutableListOf<Pair<Long, Boolean>>()
        val select = connection.prepare(
            "SELECT id, description, isMetadata FROM tasks WHERE description LIKE '%vicu-%' OR isMetadata = 1",
        )
        try {
            while (select.step()) {
                val description = if (select.isNull(1)) null else select.getText(1)
                val hidden = HiddenTasks.isHidden(description)
                if (hidden != (select.getLong(2) != 0L)) changes += select.getLong(0) to hidden
            }
        } finally {
            select.close()
        }
        if (changes.isEmpty()) return
        val update = connection.prepare("UPDATE tasks SET isMetadata = ? WHERE id = ?")
        try {
            for ((id, hidden) in changes) {
                update.bindLong(1, if (hidden) 1L else 0L)
                update.bindLong(2, id)
                update.step()
                update.reset()
                update.clearBindings()
            }
        } finally {
            update.close()
        }
    }
}
