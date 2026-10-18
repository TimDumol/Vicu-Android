import AppIntents
import SwiftUI
import WidgetKit

struct RoutineEntry: TimelineEntry {
    let date: Date
    let openURL: URL?
    let state: RoutineWidgetState?
    /// Shown instead of the routines.
    let message: String?
    let palettes: WidgetPalettes

    static let placeholder = RoutineEntry(
        date: .now,
        openURL: nil,
        state: RoutineWidgetState(date: "", occurrences: [
            RoutineWidgetItem(key: "1", routineId: "1", routineName: "Stretch", scheduledDate: "", slotId: "", slotLabel: "Morning", status: "PENDING"),
            RoutineWidgetItem(key: "2", routineId: "2", routineName: "Read", scheduledDate: "", slotId: "", slotLabel: "Evening", status: "PENDING"),
        ]),
        message: nil,
        palettes: WidgetPalettes(nil)
    )
}

struct RoutineProvider: TimelineProvider {
    func placeholder(in context: Context) -> RoutineEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (RoutineEntry) -> Void) {
        let first = entries(now: .now).first
        completion(context.isPreview && first?.state == nil ? .placeholder : first ?? .placeholder)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RoutineEntry>) -> Void) {
        let entries = entries(now: .now)
        let last = entries.last?.date ?? .now
        completion(Timeline(entries: entries, policy: .after(last.addingTimeInterval(24 * 60 * 60))))
    }

    /// Today's routines and, from midnight, tomorrow's, with the taps the app has not taken in yet.
    private func entries(now: Date) -> [RoutineEntry] {
        let catalog = WidgetStore.catalog()
        let palettes = WidgetPalettes(catalog)
        let appURL = URL(string: "\(WidgetStore.scheme)://")

        func message(_ text: String) -> [RoutineEntry] {
            [RoutineEntry(date: now, openURL: appURL, state: nil, message: text, palettes: palettes)]
        }

        guard let catalog else { return message("Open Vicu to set up the widget") }
        guard catalog.signedIn, let snapshot = WidgetStore.routines() else { return message("Log in to see your routines") }
        guard snapshot.enabled else { return message("Routines are turned off in Vicu") }

        let actions = WidgetStore.pendingActions().filter { $0.kind == WidgetAction.routineStatus }
        let start = WidgetStore.dayIndex(snapshot.days.map(\.date), now: now)
        let nextDay = Date(timeIntervalSince1970: TimeInterval(snapshot.nextDayAtMillis) / 1000)
        return snapshot.days.indices.filter { $0 >= start }.map { index in
            var state = snapshot.days[index]
            for action in actions {
                state.occurrences = state.occurrences.map { item in
                    guard item.routineId == action.routineId, item.scheduledDate == action.date, item.slotId == action.slotId else { return item }
                    var changed = item
                    changed.status = action.status
                    return changed
                }
            }
            return RoutineEntry(
                date: index == start ? now : max(now, nextDay),
                openURL: URL(string: snapshot.openUrl),
                state: state,
                message: nil,
                palettes: palettes
            )
        }
    }
}

struct RoutineWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RoutineWidget", provider: RoutineProvider()) { entry in
            RoutineWidgetView(entry: entry)
        }
        .configurationDisplayName("Routines")
        .description("Today's routines, checked off with a tap.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct RoutineWidgetView: View {
    let entry: RoutineEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var colors: WidgetColors { entry.palettes.colors(colorScheme) }

    private var rowLimit: Int {
        switch family {
        case .systemSmall: 3
        case .systemMedium: 3
        default: 8
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Routines")
                    .font(family == .systemSmall ? .subheadline.weight(.semibold) : .headline)
                    .foregroundStyle(colors.onSurface)
                Spacer(minLength: 0)
                if let state = entry.state, !state.occurrences.isEmpty {
                    Text("\(state.completedCount)/\(state.occurrences.count)")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            if let message = entry.message {
                centered(message)
            } else if let state = entry.state {
                if state.occurrences.isEmpty {
                    centered("Nothing scheduled")
                } else if state.allDone {
                    centered("All done for today")
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(state.visible.prefix(rowLimit)) { item in
                            row(item)
                        }
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(for: .widget) { colors.background }
        .widgetURL(entry.openURL)
    }

    private func centered(_ text: String) -> some View {
        VStack {
            Spacer(minLength: 0)
            Text(text)
                .font(.footnote)
                .foregroundStyle(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Spacer(minLength: 0)
        }
    }

    private func row(_ item: RoutineWidgetItem) -> some View {
        HStack(spacing: 8) {
            Button(intent: RoutineActionIntent(item: item, complete: !item.completed)) {
                Image(systemName: item.completed ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 18))
                    .foregroundStyle(item.completed ? colors.done : colors.outline)
                    .frame(width: 24, height: family == .systemSmall ? 22 : 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item.completed ? "Undo \(item.routineName)" : "Complete \(item.routineName)")

            VStack(alignment: .leading, spacing: 0) {
                Text(item.routineName)
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
                if family != .systemSmall, !item.slotLabel.isEmpty {
                    Text(item.slotLabel)
                        .font(.caption2)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

/// Logs a routine occurrence as done (or back to pending) from the widget; the app stores and
/// sends it the next time it runs.
struct RoutineActionIntent: AppIntent {
    static let title: LocalizedStringResource = "Log routine"
    static let isDiscoverable = false

    @Parameter(title: "Routine") var routineId: String
    @Parameter(title: "Date") var date: String
    @Parameter(title: "Slot") var slotId: String
    @Parameter(title: "Complete") var complete: Bool

    init() {}

    init(item: RoutineWidgetItem, complete: Bool) {
        routineId = item.routineId
        date = item.scheduledDate
        slotId = item.slotId
        self.complete = complete
    }

    func perform() async throws -> some IntentResult {
        try WidgetStore.record(WidgetAction(
            kind: WidgetAction.routineStatus,
            routineId: routineId,
            date: date,
            slotId: slotId,
            status: complete ? "COMPLETED" : "PENDING"
        ))
        return .result()
    }
}
