package com.rendyhd.vicu.ui.screens.taskdetail

import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.DueDates
import com.rendyhd.vicu.util.WhenQuickChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** The editor's quick moves and in-place reminder presets (design concept 4). */
class EditorPresetsTest {

    private val zone = TimeZone.of("Europe/Amsterdam")
    private val fmt24 = DateDisplayFormat("en-GB", hour12 = false)

    // Wednesday 21 October 2026.
    private val wednesday = LocalDate(2026, 10, 21)

    private fun due(local: String) = LocalDateTime.parse(local).toInstant(zone).toString()
    private fun dateOnly(date: LocalDate) = DueDates.dateOnlyDue(date, zone).toString()
    private fun ids(due: String?, today: LocalDate = wednesday) = quickMoves(due, today, zone).map { it.id }

    @Test
    fun `an overdue task is offered today and tomorrow and the weekend`() {
        assertEquals(
            listOf(WhenQuickChoice.Id.TODAY, WhenQuickChoice.Id.TOMORROW, WhenQuickChoice.Id.WEEKEND),
            ids(due("2026-10-20T14:00")),
        )
    }

    @Test
    fun `a task due today is offered the later days`() {
        assertEquals(
            listOf(WhenQuickChoice.Id.TOMORROW, WhenQuickChoice.Id.WEEKEND, WhenQuickChoice.Id.NEXT_WEEK),
            ids(dateOnly(wednesday)),
        )
    }

    @Test
    fun `a task without a due date is offered the first three choices`() {
        assertEquals(
            listOf(WhenQuickChoice.Id.TODAY, WhenQuickChoice.Id.TOMORROW, WhenQuickChoice.Id.WEEKEND),
            ids(null),
        )
        assertEquals(ids(null), ids("0001-01-01T00:00:00Z"))
    }

    @Test
    fun `one day is never offered twice`() {
        // On a Friday tomorrow is the weekend's Saturday: one button for it, and next week fills in.
        val friday = LocalDate(2026, 10, 23)
        val moves = quickMoves(null, friday, zone)
        assertEquals(moves.map { it.date }.distinct(), moves.map { it.date })
        assertEquals(
            listOf(WhenQuickChoice.Id.TODAY, WhenQuickChoice.Id.TOMORROW, WhenQuickChoice.Id.NEXT_WEEK),
            moves.map { it.id },
        )
    }

    @Test
    fun `a move keeps the time of day`() {
        val moved = movedDue(due("2026-10-20T14:00"), LocalDate(2026, 10, 22), zone)
        assertEquals(due("2026-10-22T14:00"), moved)
    }

    @Test
    fun `a date-only due date stays date-only and a missing one becomes date-only`() {
        val thursday = LocalDate(2026, 10, 22)
        assertEquals(dateOnly(thursday), movedDue(dateOnly(wednesday), thursday, zone))
        assertEquals(dateOnly(thursday), movedDue(null, thursday, zone))
        assertEquals(dateOnly(thursday), movedDue("0001-01-01T00:00:00Z", thursday, zone))
    }

    @Test
    fun `the snackbar says where the task went`() {
        val choices = quickMoves(null, wednesday, zone)
        assertEquals(listOf("Due today", "Due tomorrow", "Due this weekend"), choices.map(::quickMoveMessage))
    }

    @Test
    fun `reminder presets in the afternoon include the evening`() {
        val now = LocalDateTime.parse("2026-10-21T13:07:30").toInstant(zone)
        val presets = reminderPresets(due = null, now = now, zone = zone, fmt = fmt24)
        assertEquals(listOf("In 1 hour", "This evening, 18:00", "Tomorrow, 09:00"), presets.map { it.label })
        assertEquals(due("2026-10-21T14:10"), presets[0].reminder.reminder)
        assertEquals(due("2026-10-21T18:00"), presets[1].reminder.reminder)
        assertEquals(due("2026-10-22T09:00"), presets[2].reminder.reminder)
    }

    @Test
    fun `in the evening the evening preset is gone`() {
        val now = LocalDateTime.parse("2026-10-21T17:30").toInstant(zone)
        val labels = reminderPresets(null, now, zone, fmt24).map { it.label }
        assertEquals(listOf("In 1 hour", "Tomorrow, 09:00"), labels)
    }

    @Test
    fun `in 1 hour lands on whole five minutes`() {
        val exact = LocalDateTime.parse("2026-10-21T09:15").toInstant(zone)
        assertEquals(due("2026-10-21T10:15"), reminderPresets(null, exact, zone, fmt24)[0].reminder.reminder)
        val justPast = LocalDateTime.parse("2026-10-21T09:15:01").toInstant(zone)
        assertEquals(due("2026-10-21T10:20"), reminderPresets(null, justPast, zone, fmt24)[0].reminder.reminder)
    }

    @Test
    fun `relative presets need a due date`() {
        val now = LocalDateTime.parse("2026-10-21T10:00").toInstant(zone)
        val without = reminderPresets(null, now, zone, fmt24)
        assertTrue(without.none { it.reminder.relativeTo.isNotEmpty() })
        val with = reminderPresets(dateOnly(wednesday), now, zone, fmt24)
        val relative = with.filter { it.reminder.relativeTo == "due_date" }
        assertEquals(listOf("At due time", "15 minutes before", "1 hour before", "1 day before"), relative.map { it.label })
        assertEquals(listOf(0L, -900L, -3600L, -86400L), relative.map { it.reminder.relativePeriod })
    }

    @Test
    fun `times follow the 12 hour clock`() {
        val now = LocalDateTime.parse("2026-10-21T10:00").toInstant(zone)
        val labels = reminderPresets(null, now, zone, DateDisplayFormat("en-US", hour12 = true)).map { it.label }
        assertTrue("This evening, 6:00 PM" in labels, labels.toString())
        assertTrue("Tomorrow, 9:00 AM" in labels, labels.toString())
    }
}
