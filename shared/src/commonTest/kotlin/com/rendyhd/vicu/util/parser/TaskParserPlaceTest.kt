package com.rendyhd.vicu.util.parser

import com.rendyhd.vicu.location.GeoPoint
import com.rendyhd.vicu.location.LocationTrigger
import com.rendyhd.vicu.location.SavedPlace
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "When I get home" typed in the quick add: the phrase for a saved place, read as a PLACE token. */
class TaskParserPlaceTest {

    private val zone = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 21, 9, 0)
    private val home = SavedPlace("h", "Home", GeoPoint(52.37, 4.89))
    private val work = SavedPlace("w", "Work")
    private val places = listOf(home, work)

    private fun parse(text: String, config: ParserConfig = ParserConfig()) =
        TaskParser.parse(text, config, reference = now, zone = zone, places = places)

    @Test
    fun `a place phrase at the end is a place token and leaves the title`() {
        val result = parse("Call mom when I get home")

        assertEquals("Call mom", result.title)
        assertEquals(home, result.place?.place)
        assertEquals(LocationTrigger.ARRIVE, result.place?.trigger)
        val token = result.tokens.single { it.type == TokenType.PLACE }
        assertEquals("when I get home", token.raw)
        assertEquals("Call mom ".length, token.start)
    }

    @Test
    fun `the phrase goes with the other tokens wherever it is`() {
        val result = parse("Buy milk when I leave work tomorrow #Errands")

        assertEquals("Buy milk", result.title)
        assertEquals(work, result.place?.place)
        assertEquals(LocationTrigger.LEAVE, result.place?.trigger)
        assertEquals(LocalDateTime(2026, 10, 22, 9, 0).date, result.dueDate?.date)
        assertEquals("Errands", result.project)
    }

    @Test
    fun `the comma that joined the phrase leaves with it`() {
        assertEquals("call mom", parse("When I get home, call mom").title)
        assertEquals("Call mom", parse("Call mom, when I get home.").title)
        assertEquals("Buy milk, and bread", parse("Buy milk, when I get home, and bread").title)
    }

    @Test
    fun `the bare leaving is read only at the start or the end`() {
        assertEquals(LocationTrigger.LEAVE, parse("Take the keys leaving home").place?.trigger)
        assertNull(parse("Stop leaving work late").place)
        assertEquals("Stop leaving work late", parse("Stop leaving work late").title)
    }

    @Test
    fun `a place word that is part of another word is not read`() {
        assertNull(parse("Finish when I get homework done").place)
    }

    @Test
    fun `a place that is not saved stays in the title`() {
        val result = parse("Buy milk when I get to the supermarket")

        assertNull(result.place)
        assertEquals("Buy milk when I get to the supermarket", result.title)
    }

    @Test
    fun `the phrase alone is the title`() {
        val result = parse("When I get home")

        assertNull(result.place)
        assertEquals("When I get home", result.title)
    }

    @Test
    fun `a dismissed place chip leaves the words in the title`() {
        val result = parse("Call mom when I get home", ParserConfig(suppressTypes = setOf(TokenType.PLACE)))

        assertNull(result.place)
        assertEquals("Call mom when I get home", result.title)
    }

    @Test
    fun `nothing is read without places or with the parser off`() {
        assertNull(TaskParser.parse("Call mom when I get home", reference = now, zone = zone).place)
        assertNull(parse("Call mom when I get home", ParserConfig(enabled = false)).place)
    }
}
