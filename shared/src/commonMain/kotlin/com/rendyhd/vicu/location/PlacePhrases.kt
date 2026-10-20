package com.rendyhd.vicu.location

/** A spoken place phrase found in a task: what is left as the title, and the reminder it asks for. */
data class PlacePhrase(val title: String, val place: Place, val trigger: LocationTrigger)

/**
 * Reads "when I get home", "when I arrive at Work" or "when I leave the office" at the start or the
 * end of a voice command, for a place the user saved. English only, like the rest of voice add.
 * A phrase that names no saved place is left alone, so the words stay in the task's title.
 */
object PlacePhrases {
    private val ARRIVE = listOf(
        "when i get back to", "when i get back", "when i get to", "when i get",
        "when i arrive back at", "when i arrive at", "when i arrive in", "when i arrive",
        "when i'm back at", "when i'm back", "when i am back at", "when i am back",
        "when i'm at", "when i am at", "when i'm", "when i am",
        "when i come back to", "when i come back", "when i come to", "when i come",
        "when i reach", "once i get to", "once i get", "once i'm at", "once i'm", "arriving at",
    )
    private val LEAVE = listOf(
        "when i leave", "when i'm leaving", "when i am leaving", "when i get out of", "once i leave", "leaving",
    )
    private val ARTICLES = listOf("", "the ", "my ")
    private const val SEPARATORS = " ,;:-"

    fun find(text: String, places: List<Place>): PlacePhrase? {
        val spoken = normalize(text).trim().trimEnd('.', '!', '?', ',', ' ')
        val lower = spoken.lowercase()
        val candidates = buildList {
            for (place in places) {
                val name = normalize(place.name).trim().lowercase()
                if (name.isEmpty()) continue
                for (article in ARTICLES) {
                    ARRIVE.forEach { add(Candidate("$it $article$name", place, LocationTrigger.ARRIVE)) }
                    LEAVE.forEach { add(Candidate("$it $article$name", place, LocationTrigger.LEAVE)) }
                }
            }
        }.sortedByDescending { it.words.length }

        for (candidate in candidates) {
            val words = candidate.words
            val title = when {
                lower.endsWith(" $words") -> spoken.dropLast(words.length).trimEnd { it in SEPARATORS }
                lower.startsWith("$words ") || lower.startsWith("$words,") -> spoken.drop(words.length).trimStart { it in SEPARATORS }
                else -> continue
            }
            if (title.isBlank()) continue
            return PlacePhrase(title, candidate.place, candidate.trigger)
        }
        return null
    }

    /** Dictation writes curly apostrophes ("I’m"); the phrases use straight ones. */
    private fun normalize(text: String): String = text.replace('’', '\'').replace('‘', '\'')

    private class Candidate(val words: String, val place: Place, val trigger: LocationTrigger)
}
