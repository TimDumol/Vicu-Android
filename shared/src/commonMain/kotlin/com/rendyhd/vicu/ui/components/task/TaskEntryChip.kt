package com.rendyhd.vicu.ui.components.task

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A chip of the new-task sheet. [tint] is the role colour of the text token the value was read
 * from (the same colour as its highlight in the title); a value set with the chip's own picker, or
 * no value, has none. [onClear] is offered only while the chip has a value; a screen reader gets
 * it as a custom action too, next to the clear button (a 48 dp target beside the chip).
 */
@Composable
internal fun TaskEntryChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    clearDescription: String? = null,
    onClear: (() -> Unit)? = null,
    /** The rectangle of the word in the title this chip's value was read from, in root coordinates; the chip travels out of it. */
    tokenRect: (() -> Rect?)? = null,
    enabled: Boolean = true,
    /** The chip's tray is open: drawn in the secondary container, as a pressed toggle. */
    selected: Boolean = false,
    icon: ImageVector? = null,
) {
    val canClear = onClear != null && clearDescription != null
    // A tint means the value was read from the title: then the chip travels out of that word.
    val travelling = if (tokenRect != null) {
        modifier.chipTravel(fromText = tint != null, tokenRect = tokenRect)
    } else {
        modifier
    }
    if (!canClear) {
        EntryChip(label, onClick, travelling, tint, enabled, selected, icon)
        return
    }
    // The clear button sits beside the chip, not inside it: a button of 48 dp (its touch target)
    // would not fit in a 32 dp chip.
    Row(verticalAlignment = Alignment.CenterVertically) {
        EntryChip(
            label = label,
            onClick = onClick,
            modifier = travelling.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(clearDescription) {
                        onClear()
                        true
                    },
                )
            },
            tint = tint,
            enabled = enabled,
            selected = selected,
            icon = icon,
        )
        IconButton(onClick = onClear, modifier = Modifier.size(MIN_CLEAR_TARGET)) {
            Icon(
                Icons.Default.Close,
                contentDescription = clearDescription,
                modifier = Modifier.size(16.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MIN_CLEAR_TARGET = 48.dp

/** The fill of a chip whose value was read from the title: its token colour at this alpha. */
private const val TINT_FILL_ALPHA = 0.14f

@Composable
private fun EntryChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    tint: Color?,
    enabled: Boolean,
    selected: Boolean,
    icon: ImageVector?,
) {
    // Tonal, never outlined (design concept 3): a value read from the title is tinted with its
    // token's colour, the chip whose tray is open is in the secondary container, anything else
    // sits on the sheet's container colour.
    val scheme = MaterialTheme.colorScheme
    val labelColor = when {
        selected -> scheme.onSecondaryContainer
        else -> tint ?: scheme.onSurface
    }
    AssistChip(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) } },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = when {
                selected -> scheme.secondaryContainer
                tint != null -> tint.copy(alpha = TINT_FILL_ALPHA).compositeOver(scheme.surfaceContainerLow)
                else -> scheme.surfaceContainerHighest
            },
            labelColor = labelColor,
            leadingIconContentColor = if (selected) labelColor else tint ?: scheme.onSurfaceVariant,
            trailingIconContentColor = tint ?: scheme.onSurfaceVariant,
        ),
        border = null,
    )
}
