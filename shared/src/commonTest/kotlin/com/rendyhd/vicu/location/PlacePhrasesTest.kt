package com.rendyhd.vicu.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlacePhrasesTest {

    private val home = Place("h", "Home", 52.37, 4.89)
    private val work = Place("w", "Work", 52.34, 4.87)
    private val office = Place("o", "Office", 52.30, 4.80)
    private val parents = Place("p", "Mom's", 52.10, 5.10)
    private val places = listOf(home, work, office, parents)

    private fun find(text: String) = PlacePhrases.find(text, places)

    @Test
    fun `when I get home at the end becomes an arrive reminder at Home`() {
        assertEquals(PlacePhrase("Call mom", home, LocationTrigger.ARRIVE), find("Call mom when I get home"))
    }

    @Test
    fun `the common ways of saying arriving are read`() {
        listOf(
            "Water the plants when I'm home",
            "Water the plants when I am home",
            "Water the plants when I arrive home",
            "Water the plants when I get back home",
            "Water the plants once I get home.",
        ).forEach { assertEquals(PlacePhrase("Water the plants", home, LocationTrigger.ARRIVE), find(it), it) }
        assertEquals(PlacePhrase("Print the report", work, LocationTrigger.ARRIVE), find("Print the report when I arrive at work"))
        assertEquals(PlacePhrase("Print the report", office, LocationTrigger.ARRIVE), find("Print the report when I get to the office"))
    }

    @Test
    fun `leaving a place becomes a leave reminder`() {
        assertEquals(PlacePhrase("Take the laptop", work, LocationTrigger.LEAVE), find("Take the laptop when I leave work"))
        assertEquals(PlacePhrase("Lock the door", home, LocationTrigger.LEAVE), find("Lock the door when I'm leaving home"))
    }

    @Test
    fun `the phrase can come first`() {
        assertEquals(PlacePhrase("call mom", home, LocationTrigger.ARRIVE), find("When I get home, call mom"))
    }

    @Test
    fun `dictation's curly apostrophe and a place with an apostrophe both match`() {
        assertEquals(PlacePhrase("Bring the cake", parents, LocationTrigger.ARRIVE), find("Bring the cake when I’m at Mom’s"))
    }

    @Test
    fun `a place that is not saved leaves the text alone`() {
        assertNull(find("Buy milk when I get to the supermarket"))
    }

    @Test
    fun `a place word inside the sentence is not a reminder`() {
        assertNull(find("Clean the home office"))
        assertNull(find("Work on the report when I have time"))
    }

    @Test
    fun `nothing is left for a title means no match`() {
        assertNull(find("When I get home"))
    }

    @Test
    fun `without saved places nothing matches`() {
        assertNull(PlacePhrases.find("Call mom when I get home", emptyList()))
    }
}
