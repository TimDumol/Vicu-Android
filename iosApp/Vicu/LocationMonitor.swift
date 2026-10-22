import CoreLocation
import UIKit
import shared

/// Location reminders for the shared code: watches the regions it plans with CLMonitor, asks for
/// the location permission and reads the current position. Created in the app delegate before
/// `IosApp.start`, so a relaunch in the background for a region event finds the monitor (same
/// name) and gets the event.
///
/// The shared code decides everything else: which regions, and what an event shows.
final class LocationMonitor: NSObject, AppleLocationBridge, CLLocationManagerDelegate {
    /// Letters and digits only: iOS 26.5 throws (and ends the app) for any other character in a
    /// monitor name. Changing it orphans the regions watched under the old name.
    private static let monitorName = "VicuLocationReminders"
    private static let askedAlwaysKey = "location.askedAlways"

    private let manager = CLLocationManager()
    private var monitor: CLMonitor?
    /// Regions handed over before the monitor was ready.
    private var pending: [LocationRegion]?
    private var locationCallbacks: [(GeoPoint?) -> Void] = []
    private var wantsAlways = false

    override init() {
        super.init()
        manager.delegate = self
        // `Task` alone names the shared module's task model.
        _Concurrency.Task { await self.startMonitor() }
    }

    private func startMonitor() async {
        let monitor = await CLMonitor(Self.monitorName)
        await MainActor.run {
            self.monitor = monitor
            if let regions = self.pending {
                self.pending = nil
                self.applyRegions(regions: regions)
            }
        }
        do {
            for try await event in await monitor.events {
                // Each region was added assuming the state its trigger starts from (see apply), so
                // only a real arrival or departure reaches here; the shared code ignores the other direction.
                switch event.state {
                case .satisfied:
                    IosApp.shared.onRegionEvent(regionId: event.identifier, entered: true)
                case .unsatisfied:
                    IosApp.shared.onRegionEvent(regionId: event.identifier, entered: false)
                default:
                    break
                }
            }
        } catch {
            NSLog("Vicu: location monitor stopped: \(error.localizedDescription)")
        }
    }

    // MARK: AppleLocationBridge

    func applyRegions(regions: [LocationRegion]) {
        guard let monitor else {
            pending = regions
            return
        }
        _Concurrency.Task {
            let wanted = Dictionary(uniqueKeysWithValues: regions.map { ($0.id, $0) })
            let watched = Set(await monitor.identifiers)
            for id in watched where wanted[id] == nil {
                await monitor.remove(id)
            }
            // An id names the position, radius and trigger, so a region watched already is unchanged.
            for (id, region) in wanted where !watched.contains(id) {
                let condition = CLMonitor.CircularGeographicCondition(
                    center: CLLocationCoordinate2D(latitude: region.latitude, longitude: region.longitude),
                    radius: CLLocationDistance(region.radiusMeters)
                )
                // Arrive starts as "inside" and leave as "outside": the first reading then only
                // corrects the state (already there when it was set) and the next real crossing fires.
                await monitor.add(condition, identifier: id, assuming: region.notifyOnEntry ? .satisfied : .unsatisfied)
            }
        }
    }

    func authorization() -> Int32 {
        switch manager.authorizationStatus {
        case .authorizedAlways: return 2
        case .authorizedWhenInUse: return 1
        default: return 0
        }
    }

    func requestAuthorization() {
        switch manager.authorizationStatus {
        case .notDetermined:
            // iOS asks for Always only after While Using; the delegate goes on to it.
            wantsAlways = true
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse where !UserDefaults.standard.bool(forKey: Self.askedAlwaysKey):
            UserDefaults.standard.set(true, forKey: Self.askedAlwaysKey)
            manager.requestAlwaysAuthorization()
        case .authorizedAlways:
            break
        default:
            // Denied, or Always asked once already: iOS will not ask again, Settings is the way.
            if let url = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(url)
            }
        }
    }

    func currentLocation(completion: @escaping (GeoPoint?) -> Void) {
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            completion(nil)
            return
        }
        locationCallbacks.append(completion)
        if locationCallbacks.count == 1 {
            manager.requestLocation()
        }
    }

    // MARK: CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if wantsAlways, manager.authorizationStatus == .authorizedWhenInUse {
            wantsAlways = false
            UserDefaults.standard.set(true, forKey: Self.askedAlwaysKey)
            manager.requestAlwaysAuthorization()
        } else if manager.authorizationStatus != .notDetermined {
            wantsAlways = false
        }
        IosApp.shared.onLocationAuthorizationChanged()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let point = locations.last.map { GeoPoint(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude) }
        finishLocation(point)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finishLocation(nil)
    }

    private func finishLocation(_ point: GeoPoint?) {
        let callbacks = locationCallbacks
        locationCallbacks.removeAll()
        callbacks.forEach { $0(point) }
    }
}
