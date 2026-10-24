import Foundation

// What the app writes for the widgets (shared/.../widget/WidgetSnapshots.kt). The extension has
// no Kotlin: everything it shows is worked out by the app, including the due labels and colours.

struct WidgetPalette: Codable {
    let background: String
    let onSurface: String
    let onSurfaceVariant: String
    let primary: String
    let onPrimary: String
    let outline: String
    let overdue: String
    let done: String
    /// Priority 1 to 5.
    let priorities: [String]
}

struct WidgetCatalogEntry: Codable, Hashable {
    let key: String
    let name: String
    /// "list" (a smart list), "project" or "customList".
    let kind: String
    /// The id a widget configuration keeps: the key, with the account's tag for a project or
    /// custom list ("project-12@4f0c2a9e1b7d"). Missing in a catalog written before.
    let id: String?

    var entityId: String { id ?? key }
}

struct WidgetCatalog: Codable {
    let signedIn: Bool
    let views: [WidgetCatalogEntry]
    let light: WidgetPalette
    let dark: WidgetPalette
    /// The signed-in account's tag; empty when signed out, missing in a catalog written before.
    let account: String?
    /// The account whose projects and lists widgets configured before the ids carried one show.
    let untaggedAccount: String?

    /// The view a widget's configured id names, when it belongs to the account signed in now.
    /// A project or list chosen under another account has none: WidgetKit keeps a widget's
    /// configuration across a sign-out, and the new account's project with the same id is not it.
    func view(id: String) -> WidgetCatalogEntry? {
        if let entry = views.first(where: { $0.entityId == id }) { return entry }
        // Chosen before the ids carried the account: only the account signed in then has it.
        guard !id.contains("@"), account == nil || account == untaggedAccount else { return nil }
        return views.first { $0.key == id }
    }
}

struct WidgetSnapshotRow: Codable, Hashable, Identifiable {
    let id: Int64
    let title: String
    let projectName: String
    let dueLabel: String
    let overdue: Bool
    let priority: Int
    let done: Bool
}

struct WidgetSnapshotDay: Codable {
    let date: String
    let rows: [WidgetSnapshotRow]
    let totalCount: Int
    let error: String?
}

struct TaskWidgetSnapshot: Codable {
    let key: String
    let name: String
    let openUrl: String
    let addUrl: String
    /// Today, then tomorrow.
    let days: [WidgetSnapshotDay]
    let nextDayAtMillis: Int64
}

struct RoutineWidgetItem: Codable, Hashable, Identifiable {
    let key: String
    let routineId: String
    let routineName: String
    let scheduledDate: String
    let slotId: String
    let slotLabel: String
    /// PENDING, COMPLETED, SKIPPED or NOT_LOGGED.
    var status: String
    /// The check ring in the routine's own colour on the light and dark background (#RRGGBB);
    /// empty, or missing in a snapshot from an older app, without one.
    var markLight: String? = nil
    var markDark: String? = nil

    var id: String { key }
    var completed: Bool { status == "COMPLETED" }
}

struct RoutineWidgetState: Codable {
    let date: String
    var occurrences: [RoutineWidgetItem]

    var completedCount: Int { occurrences.filter(\.completed).count }
    var visible: [RoutineWidgetItem] { occurrences.filter { !$0.completed } }
    var allDone: Bool { !occurrences.isEmpty && visible.isEmpty }
}

struct RoutineWidgetSnapshot: Codable {
    let enabled: Bool
    let openUrl: String
    /// Today, then tomorrow.
    let days: [RoutineWidgetState]
    let nextDayAtMillis: Int64
}

/// A tap on a widget button, left for the app to store and send (WidgetAction in Kotlin).
struct WidgetAction: Codable {
    static let completeTask = "completeTask"
    static let routineStatus = "routineStatus"

    var kind: String
    var taskId: Int64 = 0
    var routineId: String = ""
    var date: String = ""
    var slotId: String = ""
    var status: String = ""
}

/// The App Group folder the app writes and the widget buttons leave their actions in.
enum WidgetStore {
    static let scheme: String = info("VicuUrlScheme") ?? "vicu"

    private static func info(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty, !value.hasPrefix("$(") else { return nil }
        return value
    }

    private static var root: URL? {
        guard let group = info("VicuAppGroup") else { return nil }
        return FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: group)?
            .appendingPathComponent("widgets", isDirectory: true)
    }

    private static func read<T: Decodable>(_ type: T.Type, _ path: String) -> T? {
        guard let url = root?.appendingPathComponent(path),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    static func catalog() -> WidgetCatalog? { read(WidgetCatalog.self, "catalog.json") }

    static func snapshot(key: String) -> TaskWidgetSnapshot? { read(TaskWidgetSnapshot.self, "views/\(key).json") }

    static func routines() -> RoutineWidgetSnapshot? { read(RoutineWidgetSnapshot.self, "routines.json") }

    /// The actions the app has not taken in yet, oldest first.
    static func pendingActions() -> [WidgetAction] {
        guard let dir = root?.appendingPathComponent("actions", isDirectory: true),
              let names = try? FileManager.default.contentsOfDirectory(atPath: dir.path) else { return [] }
        return names.filter { $0.hasSuffix(".json") }.sorted().compactMap { name in
            guard let data = try? Data(contentsOf: dir.appendingPathComponent(name)) else { return nil }
            return try? JSONDecoder().decode(WidgetAction.self, from: data)
        }
    }

    /// Leaves [action] for the app. Named by time so the app takes them in order; written whole
    /// and then moved into place, since the app reads only finished `.json` files.
    static func record(_ action: WidgetAction) throws {
        guard let root else { throw CocoaError(.fileNoSuchFile) }
        let dir = root.appendingPathComponent("actions", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let millis = Int64(Date().timeIntervalSince1970 * 1000)
        let name = String(format: "%013lld-%@.json", millis, UUID().uuidString.lowercased())
        let partial = dir.appendingPathComponent(name + ".part")
        try JSONEncoder().encode(action).write(to: partial)
        try FileManager.default.moveItem(at: partial, to: dir.appendingPathComponent(name))
    }

    /// The local date as the app writes it (yyyy-MM-dd).
    static func localDate(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// The index of the snapshot day that is [now]'s local day: today's while it lasts, then
    /// tomorrow's; the last one when the app has not written since.
    static func dayIndex(_ dates: [String], now: Date) -> Int {
        let today = localDate(now)
        return dates.firstIndex(of: today) ?? (dates.last.map { today > $0 } == true ? dates.count - 1 : 0)
    }
}
