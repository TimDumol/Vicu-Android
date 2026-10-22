package com.rendyhd.vicu.location

/** A spoken place phrase found in a task: what is left as the title, and the reminder it asks for. */
data class PlacePhrase(val title: String, val place: SavedPlace, val trigger: LocationTrigger)

/**
 * A place phrase in a text: the reminder it asks for, where its words are ([start] until [end],
 * what the quick add highlights) and what it takes out of the text ([consumed]: the words and the
 * comma or dash that joined them to the rest).
 */
data class PlaceMatch(
    val place: SavedPlace,
    val trigger: LocationTrigger,
    val start: Int,
    val end: Int,
    val consumed: IntRange,
)

/**
 * Reads "when I get home", "when I arrive at Work" or "when I leave the office" in a task, for a
 * place the user saved: in a voice command and in the text typed in the quick add (the parser's
 * [com.rendyhd.vicu.util.parser.TokenType.PLACE]). English only, like the rest of voice add. A
 * phrase that starts with "when" or "once" is read anywhere in the text; the bare "leaving work"
 * and "arriving at work" only at the start or the end, where they cannot be part of the sentence.
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
    private const val END_PUNCTUATION = ".!?"

    /** The voice command [text] without its place phrase, and the reminder; null when it names no saved place. */
    fun find(text: String, places: List<SavedPlace>): PlacePhrase? {
        val match = match(text, places) ?: return null
        return PlacePhrase(titleWithout(text, match.consumed), match.place, match.trigger)
    }

    /** The place phrase in [text], or null when there is none or nothing would be left for a title. */
    fun match(text: String, places: List<SavedPlace>): PlaceMatch? {
        if (places.isEmpty()) return null
        // Dictation writes curly apostrophes ("I’m"); the phrases use straight ones. One character
        // for one, so a position in the lowercased copy is the same position in the text.
        val lower = lowercase(text)
        val candidates = buildList {
            for (place in places) {
                val name = lowercase(place.name).trim()
                if (name.isEmpty()) continue
                for (article in ARTICLES) {
                    ARRIVE.forEach { add(Candidate("$it $article$name", place, LocationTrigger.ARRIVE)) }
                    LEAVE.forEach { add(Candidate("$it $article$name", place, LocationTrigger.LEAVE)) }
                }
            }
        }.sortedByDescending { it.words.length }

        for (candidate in candidates) {
            var from = 0
            while (true) {
                val start = lower.indexOf(candidate.words, from)
                if (start < 0) break
                from = start + 1
                val end = start + candidate.words.length
                if (start > 0 && lower[start - 1].isWordPart()) continue
                if (end < lower.length && lower[end].isWordPart()) continue
                val atStart = text.substring(0, start).isBlank()
                val atEnd = text.substring(end).all { it.isWhitespace() || it in END_PUNCTUATION }
                if (!candidate.anywhere && !atStart && !atEnd) continue
                val consumed = consumedRange(text, start, end, atStart, atEnd)
                if (titleWithout(text, consumed).isBlank()) continue
                return PlaceMatch(candidate.place, candidate.trigger, start, end, consumed)
            }
        }
        return null
    }

    /**
     * The phrase and what joins it to the rest: at the start the comma after it ("When I get home,
     * call mom"), at the end the comma before it and the closing full stop, in the middle a comma
     * right after it ("Buy milk, when I get home, and bread").
     */
    private fun consumedRange(text: String, start: Int, end: Int, atStart: Boolean, atEnd: Boolean): IntRange {
        var first = start
        var last = end
        when {
            atEnd -> {
                last = text.length
                while (first > 0 && text[first - 1] in SEPARATORS) first--
            }
            atStart -> while (last < text.length && text[last] in SEPARATORS) last++
            else -> {
                var next = last
                while (next < text.length && text[next] == ' ') next++
                if (next < text.length && text[next] in ",;") last = next + 1
            }
        }
        return first until last
    }

    private fun titleWithout(text: String, consumed: IntRange): String =
        (text.substring(0, consumed.first) + " " + text.substring(consumed.last + 1))
            .replace(Regex("""\s+"""), " ")
            .trim { it in SEPARATORS || it.isWhitespace() }

    /** Lowercased one character at a time (so it keeps its length), with straight apostrophes. */
    private fun lowercase(text: String): String = buildString(text.length) {
        for (c in text) append(if (c == '’' || c == '‘') '\'' else c.lowercaseChar())
    }

    private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '\''

    private class Candidate(val words: String, val place: SavedPlace, val trigger: LocationTrigger) {
        /** "when I get home" can sit inside the sentence; "leaving work" could be the task itself. */
        val anywhere = words.startsWith("when ") || words.startsWith("once ")
    }
}
