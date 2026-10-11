package com.rendyhd.vicu.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class RelationKindTest {
    @Test
    fun `wire value for precedes has one e`() {
        assertEquals("precedes", RelationKind.PRECEDES)
    }

    @Test
    fun `labels are human readable`() {
        assertEquals("Blocked by", RelationKind.label(RelationKind.BLOCKED))
        assertEquals("Duplicate of", RelationKind.label(RelationKind.DUPLICATEOF))
        assertEquals("Parent task", RelationKind.label(RelationKind.PARENTTASK))
    }

    @Test
    fun `subtask and parenttask are not user-selectable`() {
        assertFalse(RelationKind.SELECTABLE.contains(RelationKind.SUBTASK))
        assertFalse(RelationKind.SELECTABLE.contains(RelationKind.PARENTTASK))
        assertTrue(RelationKind.SELECTABLE.contains(RelationKind.BLOCKING))
    }
}
