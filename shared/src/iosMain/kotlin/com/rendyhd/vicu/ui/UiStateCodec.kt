package com.rendyhd.vicu.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Writes the values of a [androidx.compose.runtime.saveable.SaveableStateRegistry] as JSON and
 * reads them back with their exact types, the way Android's instance state keeps them in a
 * Bundle: the navigation back stack ([SavedState]), the screens' saved state and the
 * `rememberSaveable` values (snapshot states, primitives, arrays, lists and maps of those).
 *
 * Enums are saved by name when they are listed in [enums]: Kotlin/Native cannot look an enum up
 * by its class name. A value of any other type (an object Android would parcel) is saved as null,
 * which `rememberSaveable` reads as nothing saved: that piece of the UI starts fresh. Its key is
 * listed in [Encoded.skipped].
 */
@OptIn(ExperimentalMaterial3Api::class)
internal object UiStateCodec {

    class Encoded(val values: JsonObject, val skipped: List<String>)

    private class Unsupported(type: String?) : Exception("cannot save a ${type ?: "value"}")

    fun encode(values: Map<String, List<Any?>>): Encoded {
        val skipped = mutableListOf<String>()
        // One key holds the values of every rememberSaveable in a group, so one value that cannot
        // be saved must not take the others with it.
        val encoded = values.mapValues { (key, list) ->
            JsonArray(
                list.map { value ->
                    try {
                        write(value)
                    } catch (e: Unsupported) {
                        skipped += "$key: ${e.message}"
                        JsonNull
                    }
                },
            )
        }
        return Encoded(JsonObject(encoded), skipped)
    }

    fun decode(values: JsonObject): Map<String, List<Any?>> =
        values.mapValues { (_, list) -> list.jsonArray.map(::read) }

    private fun write(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> tagged("i", JsonPrimitive(value))
        is Long -> tagged("l", JsonPrimitive(value))
        is Double -> tagged("d", JsonPrimitive(value))
        is Float -> tagged("f", JsonPrimitive(value))
        is Short -> tagged("sh", JsonPrimitive(value))
        is Byte -> tagged("by", JsonPrimitive(value))
        is Char -> tagged("c", JsonPrimitive(value.toString()))
        is IntArray -> tagged("ia", JsonArray(value.map { JsonPrimitive(it) }))
        is LongArray -> tagged("la", JsonArray(value.map { JsonPrimitive(it) }))
        is BooleanArray -> tagged("ba", JsonArray(value.map { JsonPrimitive(it) }))
        is DoubleArray -> tagged("da", JsonArray(value.map { JsonPrimitive(it) }))
        is FloatArray -> tagged("fa", JsonArray(value.map { JsonPrimitive(it) }))
        is CharArray -> tagged("ca", JsonPrimitive(value.concatToString()))
        is Array<*> -> tagged("A", JsonArray(value.map(::write)))
        is SavedState -> tagged("S", JsonObject(value.read { toMap() }.mapValues { write(it.value) }))
        is List<*> -> tagged("L", JsonArray(value.map(::write)))
        is Map<*, *> -> tagged("M", JsonArray(value.map { (k, v) -> JsonArray(listOf(write(k), write(v))) }))
        // The primitive snapshot states first: they are SnapshotMutableStates as well.
        is MutableIntState -> tagged("mi", JsonPrimitive(value.intValue))
        is MutableLongState -> tagged("ml", JsonPrimitive(value.longValue))
        is MutableFloatState -> tagged("mf", JsonPrimitive(value.floatValue))
        is MutableDoubleState -> tagged("md", JsonPrimitive(value.doubleValue))
        is SnapshotMutableState<*> -> JsonObject(
            mapOf("t" to JsonPrimitive("ms"), "p" to JsonPrimitive(policyName(value)), "v" to write(value.value)),
        )
        is Enum<*> -> {
            val type = value::class.simpleName
            if (type == null || type !in enums) throw Unsupported(type)
            JsonObject(mapOf("t" to JsonPrimitive("e"), "c" to JsonPrimitive(type), "v" to JsonPrimitive(value.name)))
        }
        else -> throw Unsupported(value::class.simpleName)
    }

    private fun read(element: JsonElement): Any? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) return if (element.isString) element.content else element.boolean
        val obj = element.jsonObject
        val v = obj.getValue("v")
        return when (val tag = obj.getValue("t").jsonPrimitive.content) {
            "i" -> v.jsonPrimitive.int
            "l" -> v.jsonPrimitive.long
            "d" -> v.jsonPrimitive.double
            "f" -> v.jsonPrimitive.float
            "sh" -> v.jsonPrimitive.int.toShort()
            "by" -> v.jsonPrimitive.int.toByte()
            "c" -> v.jsonPrimitive.content.single()
            "ia" -> v.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            "la" -> v.jsonArray.map { it.jsonPrimitive.long }.toLongArray()
            "ba" -> v.jsonArray.map { it.jsonPrimitive.boolean }.toBooleanArray()
            "da" -> v.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
            "fa" -> v.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            "ca" -> v.jsonPrimitive.content.toCharArray()
            "A" -> v.jsonArray.map(::read).toTypedArray()
            "S" -> savedState(v.jsonObject.mapValues { read(it.value) })
            "L" -> v.jsonArray.map(::read)
            "M" -> v.jsonArray.associate { pair -> read(pair.jsonArray[0]) to read(pair.jsonArray[1]) }.toMutableMap()
            "mi" -> mutableIntStateOf(v.jsonPrimitive.int)
            "ml" -> mutableLongStateOf(v.jsonPrimitive.long)
            "mf" -> mutableFloatStateOf(v.jsonPrimitive.float)
            "md" -> mutableDoubleStateOf(v.jsonPrimitive.double)
            "ms" -> mutableStateOf(read(v), policy(obj.getValue("p").jsonPrimitive.content))
            "e" -> enums.getValue(obj.getValue("c").jsonPrimitive.content)(v.jsonPrimitive.content)
            else -> error("unknown tag $tag")
        }
    }

    /** The enums the UI keeps in its saved state (Material's sheet, drawer and swipe states). */
    private val enums: Map<String, (String) -> Enum<*>> = mapOf(
        "DrawerValue" to { name -> DrawerValue.valueOf(name) },
        "SheetValue" to { name -> SheetValue.valueOf(name) },
        "SwipeToDismissBoxValue" to { name -> SwipeToDismissBoxValue.valueOf(name) },
    )

    private fun tagged(tag: String, value: JsonElement) = JsonObject(mapOf("t" to JsonPrimitive(tag), "v" to value))

    private fun policyName(state: SnapshotMutableState<*>): String = when (state.policy) {
        structuralEqualityPolicy<Any?>() -> "structural"
        referentialEqualityPolicy<Any?>() -> "referential"
        neverEqualPolicy<Any?>() -> "never"
        else -> throw Unsupported("state with a custom policy")
    }

    private fun policy(name: String) = when (name) {
        "structural" -> structuralEqualityPolicy<Any?>()
        "referential" -> referentialEqualityPolicy()
        "never" -> neverEqualPolicy()
        else -> error("unknown policy $name")
    }
}
