package com.rendyhd.vicu.util

import com.rendyhd.vicu.domain.model.Task
import com.rendyhd.vicu.domain.model.TaskOrder
import kotlin.time.Instant

/**
 * Sorts a project's task list in [order], by the rules both apps share
 * (docs/cross-app-semantics-v1.md, section 12.2): due and created dates compared as instants,
 * titles lowercased, and every order ending with the position and then the task id, so equal rows
 * land in the same place on every device. The default ([TaskOrder.DUE_DATE_FIRST]) is the desktop's
 * order: dated tasks first by due date, then the undated ones in manual order.
 */
fun sortProjectTasks(tasks: List<Task>, order: TaskOrder = TaskOrder.DEFAULT): List<Task> =
    tasks.sortedWith(taskComparator(order))

/** The comparator behind [sortProjectTasks]. */
fun taskComparator(order: TaskOrder): Comparator<Task> = when (order) {
    TaskOrder.DUE_DATE_FIRST -> datedFirst.then(byDue).then(manual)
    TaskOrder.DUE_DATE_THEN_PRIORITY -> datedFirst.then(byDue).then(byPriority).then(manual)
    TaskOrder.MANUAL -> manual
    TaskOrder.PRIORITY -> byPriority.then(datedFirst).then(byDue).then(manual)
    TaskOrder.TITLE -> compareBy<Task> { it.title.lowercase() }.then(manual)
    TaskOrder.NEWEST -> createdKnownFirst.thenByDescending { createdOf(it) }.then(manual)
    TaskOrder.OLDEST -> createdKnownFirst.thenBy { createdOf(it) }.then(manual)
}

private val NULL_DATE: Instant = Instant.parse("0001-01-01T00:00:00Z")

/** The due date as an instant, or null for no due date (blank, the null date, unreadable). */
private fun dueOf(t: Task): Instant? = DateUtils.parseIsoDate(t.dueDate)?.takeIf { it > NULL_DATE }

private fun createdOf(t: Task): Instant? = DateUtils.parseIsoDate(t.created)

/** True when [t] has a due date (section 12.2): what puts it among the dated rows. */
internal fun isDatedTask(t: Task): Boolean = dueOf(t) != null

private val datedFirst: Comparator<Task> = compareBy { !isDatedTask(it) }

/** Earlier first; two undated rows are equal here. */
private val byDue: Comparator<Task> = compareBy(nullsLast()) { dueOf(it) }

/** Vikunja priorities run 0 (unset) to 5 (do now): the highest first. */
private val byPriority: Comparator<Task> = compareByDescending { it.priority }

private val createdKnownFirst: Comparator<Task> = compareBy { createdOf(it) == null }

/** The manual order: the position in the project's list view, then the id. */
private val manual: Comparator<Task> = compareBy<Task> { it.position }.thenBy { it.id }
