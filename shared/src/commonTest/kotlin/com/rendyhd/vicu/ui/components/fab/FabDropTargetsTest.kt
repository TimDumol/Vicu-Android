package com.rendyhd.vicu.ui.components.fab

import androidx.compose.ui.geometry.Rect
import com.rendyhd.vicu.util.DueDates
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a dragged + lands. */
class FabDropTargetsTest {
    private val monday = DropCandidate(Rect(0f, 100f, 400f, 148f), layer = 0, value = "monday")
    private val tuesday = DropCandidate(Rect(0f, 300f, 400f, 348f), layer = 0, value = "tuesday")

    @Test
    fun theHeaderUnderThePointGetsIt() {
        assertEquals("monday", resolveDropTarget(200f, 120f, listOf(monday, tuesday)))
        assertEquals("tuesday", resolveDropTarget(10f, 347f, listOf(monday, tuesday)))
    }

    @Test
    fun betweenHeadersNothingGetsIt() {
        assertNull(resolveDropTarget(200f, 200f, listOf(monday, tuesday)))
        assertNull(resolveDropTarget(500f, 120f, listOf(monday, tuesday)))
        assertNull(resolveDropTarget(200f, 120f, emptyList<DropCandidate<String>>()))
    }

    @Test
    fun aPinnedHeaderWinsOverTheRowsUnderIt() {
        val pinned = DropCandidate(Rect(0f, 0f, 400f, 48f), layer = 1, value = "pinned day")
        val under = DropCandidate(Rect(0f, 0f, 400f, 30f), layer = 0, value = "project under it")
        assertEquals("pinned day", resolveDropTarget(100f, 20f, listOf(under, pinned)))
    }

    @Test
    fun onOneLayerTheSmallestWins() {
        val big = DropCandidate(Rect(0f, 0f, 400f, 100f), layer = 0, value = "big")
        val small = DropCandidate(Rect(0f, 0f, 400f, 40f), layer = 0, value = "small")
        assertEquals("small", resolveDropTarget(100f, 20f, listOf(big, small)))
    }

    @Test
    fun anEmptyBoundsNeverTakesIt() {
        val gone = DropCandidate(Rect.Zero, layer = 2, value = "scrolled away")
        assertNull(resolveDropTarget(0f, 0f, listOf(gone)))
    }

    @Test
    fun aDayIsDueDateOnlyAndAProjectNeedsARealId() {
        val zone = TimeZone.of("Europe/Amsterdam")
        val friday = FabDropTarget.day(LocalDate(2026, 10, 23), zone, projectId = 7)
        assertTrue(DueDates.isDateOnly(friday.dueDate, zone))
        assertEquals(LocalDate(2026, 10, 23), DueDates.localDateOf(friday.dueDate, zone))
        assertEquals(7L, friday.projectId)
        assertEquals(FabDropTarget(projectId = 3), FabDropTarget.project(3))
        assertNull(FabDropTarget.project(0))
        assertNull(FabDropTarget.project(-1))
    }

    @Test
    fun onlyTheScreenOnTopCounts() {
        val targets = FabDropTargets()
        targets.register("a", screen = "today", layer = 0)
        targets.update("a", Rect(0f, 0f, 100f, 40f), FabDropTarget(projectId = 1))
        targets.register("b", screen = "upcoming", layer = 0)
        targets.update("b", Rect(0f, 0f, 100f, 40f), FabDropTarget(projectId = 2))
        assertTrue(targets.hasTargets("today"))
        assertEquals(FabDropTarget(projectId = 2), targets.hit("upcoming", 10f, 10f)?.second)
        assertNull(targets.hit("logbook", 10f, 10f))
        targets.unregister("b")
        assertNull(targets.hit("upcoming", 10f, 10f))
        assertTrue(!targets.hasTargets("upcoming"))
    }
}
