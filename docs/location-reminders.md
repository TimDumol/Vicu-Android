# Location reminders

A task can have one location reminder: "remind me when I arrive at (or leave) a place". The place
is a saved place (Home, Work, Parents, ...), a searched address or the current position, with a
radius of 100, 200 or 500 m (200 by default). It fires once, also when the app is closed, and
never for a task that is done or deleted.

Location reminders and saved places stay on the device. They are not in the Vikunja task, not in
the synced settings and not in backups, so another device (or the desktop app) does not show them.

## Where things are

| Piece | Where |
|---|---|
| Models (`Place`, `LocationReminder`, `LocationTrigger`, `LocationAccess`) | `shared/.../location/LocationModels.kt` |
| Stores: `SavedPlacesStore` (`saved_places`, device setting, kept on sign-out) and `LocationReminderStore` (`location_reminders`, account data, cleared on sign-out) | `location/LocationStores.kt` |
| Regions: reminders with the same position, radius and trigger share one region; at most 20 on iOS and 100 on Android, nearest first | `location/LocationRegionPlanner.kt` |
| Keeping the regions in step and turning an event into notifications | `location/LocationReminderCoordinator.kt` |
| Platform seams: `PlatformGeofencer`, `LocationPermissionPlatform`, `DeviceLocation`, `PlaceSearch`, `LocationNotifier` | `location/LocationPlatform.kt` |
| Android place search (Photon, OpenStreetMap) | `location/PhotonPlaceSearch.kt` |
| UI: the location step of the reminder picker, Settings > Notifications > Places, the disclosure sheet | `ui/components/location/` |
| Android: geofencing, receiver, permission, position, notification | `app/.../location/` |
| iOS: bridge implementations, Apple Maps search, notification | `shared/src/iosMain/.../location/IosLocation.kt` |
| iOS: CLMonitor, permission prompts, current position | `iosApp/Vicu/LocationMonitor.swift` |

## How it works

1. The editor's Reminder chip opens the reminder list; **At a location...** opens the location
   dialog (only in the task editor: quick add has no task yet). Voice add sets one too, from
   "when I get home" or "when I leave work" for a saved place (`docs/voice-input.md`). Saving writes the reminder to
   `LocationReminderStore`, keyed by the task id (a temporary id for a task made offline; it moves
   to the server's id once the task is sent, through `TempIdGenerator.realIdFor`).
2. `LocationReminderCoordinator` watches the store and the reminder tasks' done state. It drops the
   reminders of completed tasks, plans the regions and hands them to the platform. It runs at app
   start (forced), after every change, after boot on Android, and after the periodic background
   sync on both platforms (forced, and dropping reminders whose task is gone after a full sync).
3. The platform reports "entered region X" or "left region X". The coordinator shows a notification
   for each reminder of that region with the matching trigger ("Arriving at Home", "Leaving Work"),
   with **Mark Complete** and **Snooze 15 min** like a time reminder, and removes those reminders.
   Nothing shows for a task that is done or gone, or when task reminders are off in Settings.

A reminder set while you are already at the place does not fire right away: it waits for the next
arrival (Android: no initial trigger; iOS: an arrive region is added assuming "inside", a leave
region assuming "outside").

## Android

- Geofencing through Google Play services (`play-services-location`, `GeofencingClient`): ENTER for
  arrive regions, EXIT for leave regions, never expiring. Each plan replaces all geofences.
- Without Play services (a phone without Google) `LocationManager.addProximityAlert` is used; the
  registered ids are kept in the `location_regions` shared preferences to remove them later.
- Play services drops geofences after a reboot (the boot receiver plans again) and when location is
  switched off (`GEOFENCE_NOT_AVAILABLE`; the periodic sync and the next app start plan again).
- Permissions: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, then
  `ACCESS_BACKGROUND_LOCATION` ("Allow all the time"; from Android 11 the system shows its settings
  page for it). `MainActivity` registers the launcher (`AndroidLocationPermissionPlatform`).
- Search: Photon (`https://photon.komoot.io/api/` and `/reverse`) over its own Ktor client with a
  `Vicu/<version>` user agent. Only the typed text and the position rounded to two decimals are
  sent. The results show "Search by Photon · © OpenStreetMap contributors".

## iOS

- `LocationMonitor.swift` is created in the app delegate before `IosApp.start` and passed in as
  the `AppleLocationBridge`. It owns a `CLMonitor` named `vicu-location-reminders`; iOS relaunches
  the app in the background for an event, and the monitor (same name) delivers it to
  `IosApp.onRegionEvent`, which shows the notification inside a background task.
- No `location` background mode is needed. Info.plist has `NSLocationWhenInUseUsageDescription` and
  `NSLocationAlwaysAndWhenInUseUsageDescription` (from `project.yml`).
- Permission: While Using first, then Always (iOS asks for Always once; after that, and after a
  denial, the button opens Settings).
- Search: `MKLocalSearch` (near the current position when known) and `CLGeocoder` for "Use current
  location".
- The notification is posted at once with id `location:<taskId>` and kind `LOCATION` in its user
  info; the delegate opens the task on a tap and runs Complete and Snooze like a time reminder.

## Testing

- Unit tests: `LocationRegionPlannerTest`, `LocationReminderCoordinatorTest`, `PhotonPlaceSearchTest`.
- Android emulator, with the app closed: set a reminder at a place, then move the emulator there
  and back with `adb -s emulator-5554 emu geo fix <longitude> <latitude>`. Geofences need precise
  location and "Allow all the time".
- iOS simulator: Features > Location > Custom Location (or a GPX route in Xcode).
- On a device: setting a reminder while at the place (fires on the next arrival), a reboot, a
  relaunch in the background after the app was swiped away, and a task completed on another
  device (no notification).

## Store and privacy

- `docs/privacy-policy.md` says that positions and places stay on the device and what place search
  sends where.
- Google Play: the background location declaration (with a video of at most 30 seconds showing the
  disclosure, the permission and a reminder firing), the Data safety form (location: not
  collected, since it never leaves the device; the search text goes to Photon) and a mention in
  the store listing. The owner does this in the Play Console.
- App Store: the privacy labels (no location data collected).
