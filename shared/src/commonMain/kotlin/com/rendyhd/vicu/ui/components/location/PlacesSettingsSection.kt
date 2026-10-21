package com.rendyhd.vicu.ui.components.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rendyhd.vicu.location.LocationAccess
import com.rendyhd.vicu.location.PlaceSuggestion
import com.rendyhd.vicu.location.SavedPlace

/** Settings > Notifications > Places: the saved places and the location permission. */
@Composable
fun PlacesSettingsSection(viewModel: PlacesViewModel) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedPlace?>(null) }
    var deleting by remember { mutableStateOf<SavedPlace?>(null) }
    var locating by remember { mutableStateOf<SavedPlace?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Places",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = "Places for location reminders. Their names sync with your other devices; where they are stays on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Location access", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (access) {
                        LocationAccess.ALWAYS -> "Always: location reminders work while Vicu is closed"
                        LocationAccess.WHILE_USING -> "Only while using the app: location reminders need Always"
                        LocationAccess.NONE -> "Off: location reminders need Always"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (access == LocationAccess.ALWAYS) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (access != LocationAccess.ALWAYS) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = viewModel::askForAccess) { Text("Allow") }
            }
        }

        places.forEach { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClickLabel = "Rename") { renaming = place }
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge)
                    if (place.position == null) {
                        Text(
                            LocationCopy.NOT_SET,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (place.address.isNotBlank()) {
                        Text(
                            place.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (place.position == null) {
                    TextButton(onClick = { locating = place }) { Text("Set location") }
                }
                IconButton(onClick = { deleting = place }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${place.name}")
                }
            }
        }

        TextButton(
            onClick = { adding = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add place")
        }
    }

    if (adding) {
        AddPlaceDialog(viewModel = viewModel, onDone = { adding = false })
    }
    locating?.let { place ->
        AddPlaceDialog(viewModel = viewModel, onDone = { locating = null }, locate = place)
    }
    renaming?.let { place ->
        PlaceNameDialog(
            title = "Rename place",
            initial = place.name,
            onSave = { name ->
                viewModel.renamePlace(place, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${place.name}?") },
            text = { Text("It is deleted on all your devices, with the location reminders set at ${place.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    LocationDisclosureSheet(viewModel)
}

/**
 * Finds a place by search or the current position, then asks for its name. With [locate], sets
 * where that place (saved on another device) is on this phone instead.
 */
@Composable
private fun AddPlaceDialog(viewModel: PlacesViewModel, onDone: () -> Unit, locate: SavedPlace? = null) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    var found by remember { mutableStateOf<PlaceSuggestion?>(null) }

    fun close() {
        viewModel.clearSearch()
        onDone()
    }

    val picked = found
    if (picked != null && locate != null) {
        viewModel.setPosition(locate, picked)
        close()
        return
    }
    if (picked != null) {
        PlaceNameDialog(
            title = "Name this place",
            initial = picked.name.takeUnless { it == "Current location" }.orEmpty(),
            onSave = { name ->
                viewModel.addPlace(name, picked)
                close()
            },
            onDismiss = { found = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(if (locate != null) "Where is ${locate.name}?" else "Add place") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaceSearchField(
                    query = query,
                    results = results,
                    attribution = viewModel.attribution,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearSearch,
                    onPick = { found = it },
                )
                TextButton(
                    onClick = {
                        if (access == LocationAccess.NONE) {
                            viewModel.askForAccess()
                        } else {
                            viewModel.useCurrentLocation { it?.let { place -> found = place } }
                        }
                    },
                    enabled = !locating,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Finding your location…" else "Use current location")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = ::close) { Text("Cancel") } },
    )
}
