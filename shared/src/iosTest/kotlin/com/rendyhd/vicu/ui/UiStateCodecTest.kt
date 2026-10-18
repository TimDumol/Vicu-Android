package com.rendyhd.vicu.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class UiStateCodecTest {

    private fun roundTrip(values: Map<String, List<Any?>>): Map<String, List<Any?>> {
        val encoded = UiStateCodec.encode(values)
        // Through text, as the store writes it.
        val text = encoded.values.toString()
        return UiStateCodec.decode(Json.parseToJsonElement(text).jsonObject)
    }

    @Test
    fun primitivesKeepTheirTypes() {
        val restored = roundTrip(
            mapOf("k" to listOf("true", true, 7, 7L, 1.5, 2.5f, 'x', null, "")),
        ).getValue("k")
        assertEquals(listOf<Any?>("true", true, 7, 7L, 1.5, 2.5f, 'x', null, ""), restored)
        assertIs<String>(restored[0])
        assertIs<Int>(restored[2])
        assertIs<Long>(restored[3])
        assertIs<Float>(restored[5])
    }

    @Test
    fun arraysListsAndMaps() {
        val restored = roundTrip(
            mapOf(
                "a" to listOf(intArrayOf(1, 2), longArrayOf(3L), arrayOf("x", "y"), listOf(1, "two")),
                "m" to listOf(mapOf("entry-1" to mapOf("inner" to listOf(5)))),
            ),
        )
        val a = restored.getValue("a")
        assertContentEquals(intArrayOf(1, 2), a[0] as IntArray)
        assertContentEquals(longArrayOf(3L), a[1] as LongArray)
        assertEquals(listOf<Any?>("x", "y"), (a[2] as Array<*>).toList())
        assertEquals(listOf<Any?>(1, "two"), a[3])
        assertEquals(mapOf<Any?, Any?>("entry-1" to mapOf<Any?, Any?>("inner" to listOf(5))), restored.getValue("m")[0])
    }

    @Test
    fun snapshotStatesComeBackAsTheSameKind() {
        val restored = roundTrip(
            mapOf(
                "s" to listOf(
                    mutableStateOf(true),
                    mutableLongStateOf(42L),
                    mutableIntStateOf(3),
                    mutableStateOf<String?>(null, neverEqualPolicy()),
                ),
            ),
        ).getValue("s")
        assertEquals(true, (restored[0] as MutableState<*>).value)
        assertEquals(42L, (restored[1] as MutableLongState).longValue)
        assertEquals(3, (restored[2] as MutableIntState).intValue)
        val never = restored[3] as SnapshotMutableState<*>
        assertNull(never.value)
        assertSame(neverEqualPolicy<Any?>(), never.policy)
    }

    @Test
    fun savedStateKeepsNestedValues() {
        val entry = savedState(mapOf("nav-entry-state:id" to "abc", "destination" to 12, "args" to savedState(mapOf("projectId" to 6L))))
        val restored = roundTrip(mapOf("nav" to listOf(savedState(mapOf("backStack" to listOf(entry), "ids" to intArrayOf(1, 12)))))).getValue("nav")[0]
        val root = assertIs<SavedState>(restored)
        root.read {
            val back = getSavedStateList("backStack").single()
            back.read {
                assertEquals("abc", getString("nav-entry-state:id"))
                assertEquals(12, getInt("destination"))
                assertEquals(6L, getSavedState("args").read { getLong("projectId") })
            }
            assertContentEquals(intArrayOf(1, 12), getIntArray("ids"))
        }
    }

    @Test
    fun materialEnumsComeBack() {
        val restored = roundTrip(mapOf("k" to listOf(DrawerValue.Open, SheetValue.Expanded, SwipeToDismissBoxValue.EndToStart))).getValue("k")
        assertEquals(listOf<Any?>(DrawerValue.Open, SheetValue.Expanded, SwipeToDismissBoxValue.EndToStart), restored)
    }

    @Test
    fun aValueThatCannotBeSavedBecomesNothingSavedAndKeepsItsNeighbours() {
        class Opaque
        val encoded = UiStateCodec.encode(mapOf("group" to listOf(1, Opaque(), mutableStateOf("kept"))))
        val restored = UiStateCodec.decode(Json.parseToJsonElement(encoded.values.toString()).jsonObject).getValue("group")
        assertEquals(3, restored.size)
        assertEquals(1, restored[0])
        assertNull(restored[1])
        assertEquals("kept", (restored[2] as MutableState<*>).value)
        assertTrue(encoded.skipped.single().startsWith("group"))
    }
}
