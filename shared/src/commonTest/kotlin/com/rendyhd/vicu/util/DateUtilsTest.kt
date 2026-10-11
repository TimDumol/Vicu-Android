package com.rendyhd.vicu.util

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.abs

class DateUtilsTest {

    @Test
    fun `isoDaysAgo returns a parseable UTC instant about N days before now`() {
        val days = 90
        val parsed = DateUtils.parseIsoDate(DateUtils.isoDaysAgo(days))
        requireNotNull(parsed) { "isoDaysAgo should produce a parseable ISO instant" }
        val expected = Clock.System.now().minus(days.toLong(), DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        val diffSeconds = abs(parsed.epochSeconds - expected.epochSeconds)
        assertTrue(diffSeconds < 5, "expected within ~5s of now-$days days but diff was ${diffSeconds}s")
    }

    @Test
    fun `isoDaysAgo with a larger window is earlier`() {
        val recent = DateUtils.parseIsoDate(DateUtils.isoDaysAgo(30))!!
        val older = DateUtils.parseIsoDate(DateUtils.isoDaysAgo(365))!!
        assertTrue(older < recent, "365-day cutoff should be before 30-day cutoff")
    }

    @Test
    fun `formatRecurrence treats monthly - repeatMode 1 - repeatAfter 0 as recurring`() {
        // Regression: monthly recurrence carries repeatAfter==0 and was previously shown as
        // non-recurring because the gate only checked repeatAfter > 0.
        assertTrue(DateUtils.formatRecurrence(0, 1).isNotBlank())
        assertTrue(DateUtils.formatRecurrence(0, 1).contains("month", ignoreCase = true))
    }

    @Test
    fun `formatRecurrence is blank for non-recurring`() {
        assertTrue(DateUtils.formatRecurrence(0, 0).isBlank())
    }

    @Test
    fun `formatRecurrence weekly from repeatAfter`() {
        assertTrue(DateUtils.formatRecurrence(7 * 86400, 0).contains("week", ignoreCase = true))
    }

    @Test
    fun `formatRecurrence labels completion mode`() {
        assertEquals("Every week from completion", DateUtils.formatRecurrence(7 * 86400, 2))
    }

    @Test
    fun `formatRecurrence does not call fixed day intervals months`() {
        assertEquals("Every 30 weeks", DateUtils.formatRecurrence(30L * 7L * 86400L, 0))
        assertEquals("Every 60 days", DateUtils.formatRecurrence(60L * 86400L, 0))
    }
}
