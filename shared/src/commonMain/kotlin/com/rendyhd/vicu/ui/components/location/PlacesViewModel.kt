package com.rendyhd.vicu.ui.components.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rendyhd.vicu.location.DeviceLocation
import com.rendyhd.vicu.location.GeoPoint
import com.rendyhd.vicu.location.LocationAccess
import com.rendyhd.vicu.location.LocationPermissionPlatform
import com.rendyhd.vicu.location.LocationReminder
import com.rendyhd.vicu.location.LocationReminderStore
import com.rendyhd.vicu.location.Place
import com.rendyhd.vicu.location.PlaceSearch
import com.rendyhd.vicu.location.PlaceSuggestion
import com.rendyhd.vicu.location.SavedPlacesStore
import com.rendyhd.vicu.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the place search shows under its field. */
sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Searching : PlaceSearchState
    data class Results(val places: List<PlaceSuggestion>) : PlaceSearchState
    data class Failed(val message: String) : PlaceSearchState
}

/**
 * The saved places, the place search and the location permission, for the location reminder
 * dialog and Settings > Notifications > Places. Places and location reminders stay on the device.
 */
@OptIn(FlowPreview::class)
class PlacesViewModel(
    private val placesStore: SavedPlacesStore,
    private val reminderStore: LocationReminderStore,
    private val permission: LocationPermissionPlatform,
    private val search: PlaceSearch,
    private val deviceLocation: DeviceLocation,
) : ViewModel() {

    val places: StateFlow<List<Place>> =
        placesStore.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val access: StateFlow<LocationAccess> = permission.access

    val attribution: String? get() = search.attribution

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<PlaceSearchState>(PlaceSearchState.Idle)
    val results: StateFlow<PlaceSearchState> = _results.asStateFlow()

    private val _disclosureShown = MutableStateFlow(false)

    /** The sheet that explains the location permission before the system asks. */
    val disclosureShown: StateFlow<Boolean> = _disclosureShown.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /**
     * Where the device was, to rank search results. Read once in the background when allowed; a
     * search never waits for it.
     */
    private var lastPoint: GeoPoint? = null

    init {
        permission.refresh()
        if (permission.access.value != LocationAccess.NONE) {
            viewModelScope.launch { lastPoint = lastPoint ?: runCatching { deviceLocation.current() }.getOrNull() }
        }
        viewModelScope.launch {
            _query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collectLatest { text ->
                val trimmed = text.trim()
                if (trimmed.length < MIN_QUERY) {
                    _results.value = PlaceSearchState.Idle
                    return@collectLatest
                }
                _results.value = PlaceSearchState.Searching
                _results.value = try {
                    PlaceSearchState.Results(search.search(trimmed, lastPoint))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG, "Place search failed: ${e.message}")
                    PlaceSearchState.Failed("Could not search. Check the connection and try again.")
                }
            }
        }
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = PlaceSearchState.Idle
    }

    fun reminderFor(taskId: Long): Flow<LocationReminder?> = reminderStore.forTask(taskId)

    fun saveReminder(reminder: LocationReminder) {
        viewModelScope.launch { reminderStore.put(reminder) }
    }

    fun removeReminder(taskId: Long) {
        viewModelScope.launch { reminderStore.remove(taskId) }
    }

    /** Saves [suggestion] as a place called [name]; [then] gets the new place. */
    fun addPlace(name: String, suggestion: PlaceSuggestion, then: (Place) -> Unit = {}) {
        viewModelScope.launch { then(placesStore.add(name, suggestion.point, suggestion.address)) }
    }

    fun renamePlace(place: Place, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            placesStore.rename(place.id, name)
            reminderStore.placeRenamed(place.copy(name = name.trim()))
        }
    }

    fun deletePlace(place: Place) {
        viewModelScope.launch {
            placesStore.delete(place.id)
            reminderStore.placeDeleted(place.id)
        }
    }

    /** The device's position as a place ("Current location" when nothing is known about it), or null. */
    fun useCurrentLocation(onFound: (PlaceSuggestion?) -> Unit) {
        viewModelScope.launch {
            _locating.value = true
            val found = try {
                deviceLocation.current()?.let { point ->
                    lastPoint = point
                    val named = runCatching { search.reverse(point) }.getOrNull()
                    named?.copy(latitude = point.latitude, longitude = point.longitude)
                        ?: PlaceSuggestion("Current location", "", point.latitude, point.longitude)
                }
            } finally {
                _locating.value = false
            }
            onFound(found)
        }
    }

    /** Explains the permission first, unless the app already has Always. */
    fun askForAccess() {
        permission.refresh()
        if (permission.access.value != LocationAccess.ALWAYS) _disclosureShown.value = true
    }

    fun confirmDisclosure() {
        _disclosureShown.value = false
        if (!permission.request()) permission.openSettings()
    }

    fun dismissDisclosure() {
        _disclosureShown.value = false
    }

    fun openSettings() = permission.openSettings()

    fun refreshAccess() = permission.refresh()

    private companion object {
        const val TAG = "PlacesViewModel"
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MIN_QUERY = 3
    }
}
