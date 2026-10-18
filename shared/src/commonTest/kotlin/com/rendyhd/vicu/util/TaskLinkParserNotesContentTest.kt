package com.rendyhd.vicu.util

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class TaskLinkParserNotesContentTest {

    @Test
    fun `blank or null description has no notes`() {
        assertFalse(TaskLinkParser.hasNotesContent(null))
        assertFalse(TaskLinkParser.hasNotesContent(""))
        assertFalse(TaskLinkParser.hasNotesContent("   "))
    }

    @Test
    fun `empty html has no notes`() {
        assertFalse(TaskLinkParser.hasNotesContent("<p></p>"))
        assertFalse(TaskLinkParser.hasNotesContent("<p><br></p>"))
        assertFalse(TaskLinkParser.hasNotesContent("<p>&nbsp;</p>"))
        assertFalse(TaskLinkParser.hasNotesContent("<div>\n  \n</div>"))
    }

    @Test
    fun `real text content has notes`() {
        assertTrue(TaskLinkParser.hasNotesContent("<p>Buy milk</p>"))
        assertTrue(TaskLinkParser.hasNotesContent("plain text"))
    }
}
