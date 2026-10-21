package com.rendyhd.vicu.ui.components.fab

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.rendyhd.vicu.util.DueDates
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What dropping the + on a header presets in quick add: a due date, a project, or both. */
@Immutable
data class FabDropTarget(val dueDate: String? = null, val projectId: Long? = null) {
    companion object {
        /** A day header: the task is due that day, date-only. */
        fun day(date: LocalDate, zone: TimeZone, projectId: Long? = null): FabDropTarget =
            FabDropTarget(dueDate = DueDates.dateOnlyDue(date, zone).toString(), projectId = projectId)

        /** A project header. Null for a group without a real project. */
        fun project(projectId: Long): FabDropTarget? = if (projectId > 0) FabDropTarget(projectId = projectId) else null
    }
}

/** A target as the hit test sees it: its bounds in root coordinates, and the [layer] it is drawn on. */
internal class DropCandidate<T>(val bounds: Rect, val layer: Int, val value: T)

/**
 * The target under ([x], [y]): the one whose bounds hold the point, on the highest layer (a pinned
 * day header over the rows scrolling under it), the smallest when two are on the same layer. Null
 * when the point is on none: dropping there does nothing.
 */
internal fun <T> resolveDropTarget(x: Float, y: Float, candidates: Collection<DropCandidate<T>>): T? =
    candidates
        .filter { !it.bounds.isEmpty && it.bounds.contains(Offset(x, y)) }
        .sortedWith(compareByDescending<DropCandidate<T>> { it.layer }.thenBy { it.bounds.width * it.bounds.height })
        .firstOrNull()
        ?.value

/**
 * The headers of the screen on top that take a dropped + (Upcoming's days, project sections),
 * registered with [fabDropTarget]. The + button asks [hasTargets] whether a move drags it, and
 * [hit] where it was dropped. Only the screen given (the back stack entry on top) counts, so a
 * screen still fading out under the new one is never a target.
 */
@Stable
class FabDropTargets {
    private class Entry(val screen: String, val layer: Int) {
        var bounds: Rect = Rect.Zero
        var target: FabDropTarget? = null
    }

    private val entries = HashMap<Any, Entry>()

    /** A + is being dragged: every target shows that it takes it. */
    var dragging by mutableStateOf(false)
        internal set

    /** The target the + is over now (its key), drawn filled. */
    var hovered by mutableStateOf<Any?>(null)
        internal set

    fun hasTargets(screen: String?): Boolean =
        screen != null && entries.values.any { it.screen == screen && it.target != null && !it.bounds.isEmpty }

    /** The key and target under ([x], [y]) in root coordinates, among [screen]'s targets. */
    internal fun hit(screen: String?, x: Float, y: Float): Pair<Any, FabDropTarget>? {
        if (screen == null) return null
        val candidates = entries.mapNotNull { (key, entry) ->
            val target = entry.target ?: return@mapNotNull null
            if (entry.screen != screen) null else DropCandidate(entry.bounds, entry.layer, key to target)
        }
        return resolveDropTarget(x, y, candidates)
    }

    internal fun register(key: Any, screen: String, layer: Int) {
        entries[key] = Entry(screen, layer)
    }

    internal fun update(key: Any, bounds: Rect?, target: FabDropTarget?) {
        val entry = entries[key] ?: return
        if (bounds != null) entry.bounds = bounds
        entry.target = target
    }

    internal fun unregister(key: Any) {
        entries.remove(key)
        if (hovered === key) hovered = null
    }
}

/** The app's drop targets; null outside the app (no + to drag). */
val LocalFabDropTargets = staticCompositionLocalOf<FabDropTargets?> { null }

/**
 * Makes this header a place to drop the + button on: dropping opens quick add with [target]'s date
 * and project. While a + is dragged the header shows a rounded outline, and fills when the + is over
 * it. The colours change at once, without movement, so there is nothing to reduce. A null [target]
 * leaves the header as it is. [layer] is 1 for a pinned header drawn over the rows.
 */
@Composable
fun Modifier.fabDropTarget(target: FabDropTarget?, layer: Int = 0): Modifier {
    val targets = LocalFabDropTargets.current ?: return this
    val screen = (LocalLifecycleOwner.current as? NavBackStackEntry)?.id ?: return this
    val key = remember { Any() }
    val currentTarget by rememberUpdatedState(target)
    DisposableEffect(targets, screen, layer) {
        targets.register(key, screen, layer)
        onDispose { targets.unregister(key) }
    }
    SideEffect { targets.update(key, null, target) }
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.primary
    // A light tint of the primary: the header's own text stays readable on it in both themes.
    val fill = scheme.primary.copy(alpha = HOVER_FILL_ALPHA)
    return this
        .onGloballyPositioned { targets.update(key, it.boundsInRoot(), currentTarget) }
        .drawBehind {
            if (!targets.dragging || currentTarget == null) return@drawBehind
            val inset = 4.dp.toPx()
            val radius = CornerRadius(12.dp.toPx())
            val topLeft = Offset(inset, inset / 2)
            val area = Size(size.width - inset * 2, size.height - inset)
            if (targets.hovered === key) drawRoundRect(fill, topLeft, area, radius)
            drawRoundRect(
                color = outline,
                topLeft = topLeft,
                size = area,
                cornerRadius = radius,
                alpha = if (targets.hovered === key) 1f else 0.5f,
                style = Stroke(width = if (targets.hovered === key) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
}

private const val HOVER_FILL_ALPHA = 0.14f
