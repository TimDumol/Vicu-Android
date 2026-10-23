package com.rendyhd.vicu.ui.screens.taskentry

import com.rendyhd.vicu.domain.model.TaskReminder
import com.rendyhd.vicu.location.LocationTrigger
import com.rendyhd.vicu.location.SavedPlace
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.WhenValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The When tray's tiles and the Remind chip's words (design concept 3). */
class QuickAddChoicesTest {

    private val fmt = DateDisplayFormat(localeTag = "en-US", hour12 = false)

    // Thursday 22 October 2026.
    private val today = LocalDate(2026, 10, 22)

    @Test
    fun `before six the tray offers this evening`() {
        val tiles = whenTiles(WhenValue.EMPTY, today, LocalTime(15, 0), fmt)

        assertEquals(
            listOf(
                WhenTile.Id.TODAY,
                WhenTile.Id.EVENING,
                WhenTile.Id.TOMORROW,
                WhenTile.Id.WEEKEND,
                WhenTile.Id.NEXT_WEEK,
                WhenTile.Id.NO_DATE,
            ),
            tiles.map { it.id },
        )
        val evening = tiles.single { it.id == WhenTile.Id.EVENING }
        assertEquals(WhenValue(today, LocalTime(18, 0)), evening.value)
        assertEquals("18:00", evening.hint)
        assertNull(tiles.single { it.id == WhenTile.Id.NO_DATE }.value)
    }

    @Test
    fun `from six this evening is gone`() {
        val tiles = whenTiles(WhenValue.EMPTY, today, LocalTime(18, 0), fmt)

        assertEquals(null, tiles.firstOrNull { it.id == WhenTile.Id.EVENING })
        assertEquals(5, tiles.size)
    }

    @Test
    fun `a day tile keeps the time already picked`() {
        val current = WhenValue(today, LocalTime(9, 0))

        val tomorrow = whenTiles(current, today, LocalTime(8, 0), fmt).single { it.id == WhenTile.Id.TOMORROW }

        assertEquals(WhenValue(LocalDate(2026, 10, 23), LocalTime(9, 0)), tomorrow.value)
    }

    @Test
    fun `the tile for the current value is picked`() {
        val tiles = whenTiles(WhenValue.EMPTY, today, LocalTime(8, 0), fmt)

        assertEquals(WhenTile.Id.NO_DATE, selectedTile(tiles, WhenValue.EMPTY, today))
        assertEquals(WhenTile.Id.TODAY, selectedTile(tiles, WhenValue(today, null), today))
        assertEquals(WhenTile.Id.EVENING, selectedTile(tiles, WhenValue(today, LocalTime(18, 0)), today))
        assertEquals(WhenTile.Id.TOMORROW, selectedTile(tiles, WhenValue(LocalDate(2026, 10, 23), null), today))
        // Saturday is this weekend; Monday 26 is next week; a date further out is no tile.
        assertEquals(WhenTile.Id.WEEKEND, selectedTile(tiles, WhenValue(LocalDate(2026, 10, 24), null), today))
        assertEquals(WhenTile.Id.NEXT_WEEK, selectedTile(tiles, WhenValue(LocalDate(2026, 10, 26), null), today))
        assertNull(selectedTile(tiles, WhenValue(LocalDate(2026, 11, 3), null), today))
    }

    @Test
    fun `on a Friday the Saturday tile is tomorrow`() {
        val friday = LocalDate(2026, 10, 23)
        val tiles = whenTiles(WhenValue.EMPTY, friday, LocalTime(8, 0), fmt)

        assertEquals(WhenTile.Id.TOMORROW, selectedTile(tiles, WhenValue(LocalDate(2026, 10, 24), null), friday))
    }

    @Test
    fun `the remind chip says the one reminder and counts more`() {
        val home = PlacePick(SavedPlace(id = "h", name = "Home"), LocationTrigger.ARRIVE)
        val beforeDue = TaskReminder(relativePeriod = -3600, relativeTo = "due_date")

        assertNull(entryRemindWords(emptyList(), null, fmt))
        assertEquals("Arrive: Home", entryRemindWords(emptyList(), home, fmt))
        assertEquals("1 hour before", entryRemindWords(listOf(beforeDue), null, fmt))
        assertEquals("2 reminders", entryRemindWords(listOf(beforeDue), home, fmt))
        assertEquals("Leave: Work", entryPlaceLabel("Work", LocationTrigger.LEAVE))
    }
}
