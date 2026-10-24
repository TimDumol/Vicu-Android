package com.rendyhd.vicu.widget

import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.ui.theme.contrastRatio
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.parseHexColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetSnapshotsTest {
    private val zone = TimeZone.of("Europe/Amsterdam")
    private val days = ClockDay(LocalDate(2026, 10, 12), zone) to ClockDay(LocalDate(2026, 10, 13), zone)
    private val format = DateDisplayFormat("en-GB", hour12 = false)

    private fun state(type: WidgetViewType, id: String = "", vararg tasks: WidgetTaskItem, smartAdd: Boolean = true, contextNav: Boolean = true, addTo: Long = 0) =
        TaskWidgetState(viewType = type, viewId = id, viewName = "View", tasks = tasks.toList(), totalCount = tasks.size, smartAdd = smartAdd, contextNav = contextNav, addToProjectId = addTo)

    @Test
    fun rowsArePhrasedForEachDay() {
        // Due 12 October, 15:00 local: today on the 12th, overdue on the 13th.
        val task = WidgetTaskItem(id = 7, title = "Call", dueDate = "2026-10-12T13:00:00Z", priority = 3)
        val snapshot = WidgetSnapshots.snapshot(state(WidgetViewType.TODAY, "", task), state(WidgetViewType.TODAY, "", task), days, "vicu", format)
        val (today, tomorrow) = snapshot.days
        assertEquals("2026-10-12", today.date)
        assertFalse(today.rows.single().overdue)
        assertTrue(tomorrow.rows.single().overdue)
        assertTrue(today.rows.single().dueLabel.contains("15:00"))
        assertEquals(3, today.rows.single().priority)
        // Local midnight in Amsterdam (UTC+2 in October).
        assertEquals(kotlin.time.Instant.parse("2026-10-12T22:00:00Z").toEpochMilliseconds(), snapshot.nextDayAtMillis)
    }

    @Test
    fun linksFollowTheWidgetSettings() {
        val project = WidgetSnapshots.snapshot(state(WidgetViewType.PROJECT, "12"), state(WidgetViewType.PROJECT, "12"), days, "vicu", format)
        assertEquals("project-12", project.key)
        assertEquals("vicu://view/PROJECT/12", project.openUrl)
        assertEquals("vicu://add?project=12", project.addUrl)

        val plain = WidgetSnapshots.snapshot(
            state(WidgetViewType.PROJECT, "12", smartAdd = false, contextNav = false),
            state(WidgetViewType.PROJECT, "12", smartAdd = false, contextNav = false),
            days, "vicu", format,
        )
        assertEquals("vicu://", plain.openUrl)
        assertEquals("vicu://add", plain.addUrl)

        val list = WidgetSnapshots.snapshot(state(WidgetViewType.CUSTOM_LIST, "a/b", addTo = 4), state(WidgetViewType.CUSTOM_LIST, "a/b", addTo = 4), days, "vicu", format)
        assertEquals("list-a_b", list.key)
        assertEquals("vicu://add?project=4", list.addUrl)
    }

    @Test
    fun palettesAreHexColours() {
        val (light, dark) = WidgetSnapshots.palettes()
        assertEquals(5, light.priorities.size)
        assertTrue(Regex("#[0-9A-F]{6}").matches(light.primary))
        assertTrue(light.background != dark.background)
    }

    @Test
    fun routineRingsAreWorkedOutForBothBackgrounds() {
        fun item(key: String, color: String) = RoutineWidgetItem(
            key = key, routineId = key, routineName = key, scheduledDate = "2026-10-12", slotId = "s",
            slotLabel = "08:00", status = OccurrenceStatus.PENDING, color = color,
        )
        val day = WidgetSnapshots.routineDay(RoutineWidgetState("2026-10-12", listOf(item("blue", "#007AFF"), item("yellow", "#eec300"), item("none", ""))))
        val (blue, yellow, none) = day.occurrences
        assertEquals("#007AFF", blue.markLight)
        assertTrue(Regex("#[0-9A-F]{6}").matches(blue.markDark))
        // Yellow does not read on the light background as it is, so it is darkened there.
        val light = WidgetSnapshots.palettes().first
        assertTrue(contrastRatio(parseHexColor(yellow.markLight)!!, parseHexColor(light.background)!!) >= 3.0)
        assertTrue(yellow.markLight != "#EEC300")
        assertEquals("", none.markLight)
        assertEquals("", none.markDark)
    }
}
