package com.rendyhd.vicu.ui.components.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rendyhd.vicu.location.LocationAccess
import com.rendyhd.vicu.location.LocationReminder
import com.rendyhd.vicu.location.LocationTrigger
import com.rendyhd.vicu.location.PlaceReminder
import com.rendyhd.vicu.location.PlaceSuggestion
import com.rendyhd.vicu.location.SavedPlace

/**
 * Sets the one location reminder of [taskId]: arrive at or leave a saved place, a searched place
 * or the current position, within 100, 200 or 500 m. [existing] is the reminder it has now. A
 * saved place can be picked even when this phone does not know where it is (saved on another
 * phone): the reminder syncs, and this phone reminds there once the place is set here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationReminderDialog(
    taskId: Long,
    existing: PlaceReminder?,
    viewModel: PlacesViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()

    var trigger by rememberSaveable { mutableStateOf((existing?.trigger ?: LocationTrigger.ARRIVE).name) }
    var radius by rememberSaveable { mutableStateOf(existing?.radiusMeters ?: LocationReminder.DEFAULT_RADIUS_METERS) }
    // The chosen spot: a saved place (its id) or a searched one (no id).
    var chosenName by rememberSaveable { mutableStateOf(existing?.placeName.orEmpty()) }
    var chosenAddress by rememberSaveable { mutableStateOf("") }
    var chosenLat by rememberSaveable { mutableStateOf(existing?.position?.latitude ?: 0.0) }
    var chosenLon by rememberSaveable { mutableStateOf(existing?.position?.longitude ?: 0.0) }
    var chosenPlaceId by rememberSaveable { mutableStateOf(existing?.placeId) }
    var chosenLocated by rememberSaveable { mutableStateOf(existing == null || existing.position != null) }
    var naming by remember { mutableStateOf<PlaceSuggestion?>(null) }
    val hasChoice = chosenName.isNotBlank()

    fun choose(suggestion: PlaceSuggestion, placeId: String?) {
        chosenName = suggestion.name
        chosenAddress = suggestion.address
        chosenLat = suggestion.latitude
        chosenLon = suggestion.longitude
        chosenPlaceId = placeId
        chosenLocated = true
        viewModel.clearSearch()
    }

    fun choose(place: SavedPlace) {
        val position = place.position
        if (position != null) {
            choose(PlaceSuggestion(place.name, place.address, position.latitude, position.longitude), place.id)
        } else {
            choose(PlaceSuggestion(place.name, "", 0.0, 0.0), place.id)
            chosenLocated = false
        }
    }

    // Before anything is chosen: the permission is what makes the reminder work at all.
    LaunchedEffect(Unit) { viewModel.askForAccess() }
    // Back from the system settings page.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearSearch()
            onDismiss()
        },
        title = { Text("Location reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (access != LocationAccess.ALWAYS) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp)) {
                            Text(
                                LocationCopy.ACCESS_NEEDED,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            TextButton(onClick = viewModel::askForAccess, modifier = Modifier.align(Alignment.End)) {
                                Text("Allow")
                            }
                        }
                    }
                }

                Text("Remind me", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = trigger == LocationTrigger.ARRIVE.name,
                        onClick = { trigger = LocationTrigger.ARRIVE.name },
                        label = { Text("When I arrive") },
                    )
                    FilterChip(
                        selected = trigger == LocationTrigger.LEAVE.name,
                        onClick = { trigger = LocationTrigger.LEAVE.name },
                        label = { Text("When I leave") },
                    )
                }

                if (hasChoice) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chosenName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (chosenAddress.isNotBlank()) {
                                Text(
                                    chosenAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!chosenLocated) {
                        Text(
                            LocationCopy.notOnThisPhone(chosenName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (chosenPlaceId == null) {
                        TextButton(onClick = {
                            naming = PlaceSuggestion(chosenName, chosenAddress, chosenLat, chosenLon)
                        }) { Text("Save as place…") }
                    }
                }

                Text("Places", style = MaterialTheme.typography.labelLarge)
                if (places.isEmpty()) {
                    Text(
                        "Save places like Home or Work to pick them quickly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        places.forEach { place ->
                            FilterChip(
                                selected = chosenPlaceId == place.id,
                                onClick = { choose(place) },
                                label = { Text(place.name) },
                            )
                        }
                    }
                }

                PlaceSearchField(
                    query = query,
                    results = results,
                    attribution = viewModel.attribution,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearSearch,
                    onPick = { choose(it, null) },
                )

                TextButton(
                    onClick = {
                        if (access == LocationAccess.NONE) {
                            viewModel.askForAccess()
                        } else {
                            viewModel.useCurrentLocation { found -> found?.let { choose(it, null) } }
                        }
                    },
                    enabled = !locating,
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Finding your location…" else "Use current location")
                }

                Text("Distance", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocationReminder.RADIUS_OPTIONS.forEach { meters ->
                        FilterChip(
                            selected = radius == meters,
                            onClick = { radius = meters },
                            label = { Text("$meters m") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasChoice,
                onClick = {
                    viewModel.saveReminder(
                        LocationReminder(
                            taskId = taskId,
                            placeId = chosenPlaceId,
                            placeName = chosenName,
                            latitude = chosenLat,
                            longitude = chosenLon,
                            radiusMeters = radius,
                            trigger = LocationTrigger.valueOf(trigger),
                        ),
                    )
                    viewModel.clearSearch()
                    onSaved()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = {
                        viewModel.removeReminder(taskId)
                        viewModel.clearSearch()
                        onDismiss()
                    }) { Text("Remove") }
                }
                TextButton(onClick = {
                    viewModel.clearSearch()
                    onDismiss()
                }) { Text("Cancel") }
            }
        },
    )

    naming?.let { suggestion ->
        PlaceNameDialog(
            title = "Save place",
            initial = suggestion.name.takeUnless { it == "Current location" }.orEmpty(),
            onSave = { name ->
                viewModel.addPlace(name, suggestion) { place -> choose(place) }
                naming = null
            },
            onDismiss = { naming = null },
        )
    }

    LocationDisclosureSheet(viewModel)
}

/** The search field and its results, shared by the reminder dialog and Settings' Add place. */
@Composable
internal fun PlaceSearchField(
    query: String,
    results: PlaceSearchState,
    attribution: String?,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onPick: (PlaceSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search places", maxLines = 1) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") } }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
        when (results) {
            PlaceSearchState.Idle -> {}
            PlaceSearchState.Searching -> SearchNote("Searching…")
            is PlaceSearchState.Failed -> SearchNote(results.message, error = true)
            is PlaceSearchState.Results -> {
                if (results.places.isEmpty()) {
                    SearchNote("No places found")
                } else {
                    results.places.take(MAX_RESULTS).forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onPick(place) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.address.isNotBlank()) {
                                    Text(
                                        place.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    attribution?.let { SearchNote(it) }
                }
            }
        }
    }
}

@Composable
private fun SearchNote(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** Asks for a place's name (saving a searched place, renaming one). */
@Composable
internal fun PlaceNameDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The cursor after the suggested name, so typing adds to it and a backspace removes it.
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("Home, Work, Parents…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.text.trim()) }, enabled = name.text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MAX_RESULTS = 6
