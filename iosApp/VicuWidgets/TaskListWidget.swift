import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Configuration

/// A view a task list widget can show: a smart list, a project or a custom list.
struct ViewEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "List"
    static let defaultQuery = ViewQuery()

    let id: String
    let name: String
    let kind: String

    var displayRepresentation: DisplayRepresentation {
        switch kind {
        case "project": DisplayRepresentation(title: "\(name)", subtitle: "Project")
        case "customList": DisplayRepresentation(title: "\(name)", subtitle: "Custom list")
        default: DisplayRepresentation(title: "\(name)")
        }
    }

    static let today = ViewEntity(id: "today", name: "Today", kind: "list")

    init(id: String, name: String, kind: String) {
        self.id = id
        self.name = name
        self.kind = kind
    }

    init(_ entry: WidgetCatalogEntry) {
        self.init(id: entry.entityId, name: entry.name, kind: entry.kind)
    }
}

/// The views from the app's catalog. One that is gone (a deleted project, or one of another
/// account) still resolves, so the widget keeps it and says it is not available instead of
/// quietly switching to Today.
struct ViewQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [ViewEntity] {
        let catalog = WidgetStore.catalog()
        return identifiers.map { id in
            catalog?.view(id: id).map { ViewEntity(id: id, name: $0.name, kind: $0.kind) }
                ?? ViewEntity(id: id, name: "Not available", kind: "")
        }
    }

    func suggestedEntities() async throws -> [ViewEntity] {
        let views = WidgetStore.catalog()?.views ?? []
        return views.isEmpty ? [.today] : views.map(ViewEntity.init)
    }

    func defaultResult() async -> ViewEntity? { .today }
}

struct SelectViewIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Choose a list"
    static let description = IntentDescription("The list the widget shows.")

    @Parameter(title: "List")
    var view: ViewEntity?

    init() {}

    init(view: ViewEntity) {
        self.view = view
    }
}

// MARK: - Timeline

struct TaskListEntry: TimelineEntry {
    let date: Date
    let name: String
    let openURL: URL?
    let addURL: URL?
    let rows: [WidgetSnapshotRow]
    let totalCount: Int
    /// Shown instead of the rows.
    let message: String?
    let palettes: WidgetPalettes

    static let placeholder = TaskListEntry(
        date: .now,
        name: "Today",
        openURL: nil,
        addURL: nil,
        rows: [
            WidgetSnapshotRow(id: 1, title: "Plan the week", projectName: "Inbox", dueLabel: "Today", overdue: false, priority: 2, done: false),
            WidgetSnapshotRow(id: 2, title: "Call the plumber", projectName: "Home", dueLabel: "Today 14:00", overdue: false, priority: 0, done: false),
            WidgetSnapshotRow(id: 3, title: "Send the invoice", projectName: "Work", dueLabel: "Yesterday", overdue: true, priority: 4, done: false),
        ],
        totalCount: 3,
        message: nil,
        palettes: WidgetPalettes(nil)
    )
}

struct TaskListProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> TaskListEntry { .placeholder }

    func snapshot(for configuration: SelectViewIntent, in context: Context) async -> TaskListEntry {
        let entries = entries(for: configuration, now: .now)
        if context.isPreview, entries.first?.message != nil { return .placeholder }
        return entries.first ?? .placeholder
    }

    func timeline(for configuration: SelectViewIntent, in context: Context) async -> Timeline<TaskListEntry> {
        let entries = entries(for: configuration, now: .now)
        let last = entries.last?.date ?? .now
        return Timeline(entries: entries, policy: .after(last.addingTimeInterval(24 * 60 * 60)))
    }

    /// One entry for the rest of today and one from midnight, from the app's snapshot of the view,
    /// with the rows completed from the widget but not yet taken in by the app left out.
    private func entries(for configuration: SelectViewIntent, now: Date) -> [TaskListEntry] {
        let catalog = WidgetStore.catalog()
        let palettes = WidgetPalettes(catalog)
        let view = configuration.view ?? .today

        func message(_ text: String, name: String = view.name) -> [TaskListEntry] {
            [TaskListEntry(date: now, name: name, openURL: URL(string: "\(WidgetStore.scheme)://"), addURL: nil,
                           rows: [], totalCount: 0, message: text, palettes: palettes)]
        }

        guard let catalog else { return message("Open Vicu to set up the widget", name: "Vicu") }
        guard catalog.signedIn else { return message("Log in to see your tasks") }
        guard let entry = catalog.view(id: view.id), let snapshot = WidgetStore.snapshot(key: entry.key) else {
            return message("This list is no longer available. Edit the widget to choose another.")
        }

        let completed = Set(WidgetStore.pendingActions().filter { $0.kind == WidgetAction.completeTask }.map(\.taskId))
        let start = WidgetStore.dayIndex(snapshot.days.map(\.date), now: now)
        let nextDay = Date(timeIntervalSince1970: TimeInterval(snapshot.nextDayAtMillis) / 1000)
        return snapshot.days.indices.filter { $0 >= start }.map { index in
            let day = snapshot.days[index]
            let rows = day.rows.filter { !completed.contains($0.id) }
            return TaskListEntry(
                date: index == start ? now : max(now, nextDay),
                name: snapshot.name,
                openURL: URL(string: snapshot.openUrl),
                addURL: URL(string: snapshot.addUrl),
                rows: rows,
                totalCount: max(day.totalCount - (day.rows.count - rows.count), rows.count),
                message: day.error,
                palettes: palettes
            )
        }
    }
}

// MARK: - Widget

struct TaskListWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "TaskListWidget", intent: SelectViewIntent.self, provider: TaskListProvider()) { entry in
            TaskListWidgetView(entry: entry)
        }
        .configurationDisplayName("Tasks")
        .description("A list of your tasks: Today, Inbox, Upcoming, Anytime, a project or a custom list.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct TaskListWidgetView: View {
    let entry: TaskListEntry
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var colorScheme

    private var colors: WidgetColors { entry.palettes.colors(colorScheme) }

    private var rowLimit: Int {
        switch family {
        case .systemSmall: 3
        case .systemMedium: 4
        default: 7
        }
    }

    /// The count in the header says how many there are; the medium widget has no room for a footer.
    private var showsMore: Bool {
        family != .systemMedium && entry.totalCount > min(rowLimit, entry.rows.count)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: family == .systemSmall ? 6 : 8) {
            header
            if let message = entry.message {
                Spacer(minLength: 0)
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
                Spacer(minLength: 0)
            } else if entry.rows.isEmpty {
                Spacer(minLength: 0)
                Text("No tasks")
                    .font(.footnote)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .center)
                Spacer(minLength: 0)
            } else {
                VStack(alignment: .leading, spacing: family == .systemLarge ? 6 : 4) {
                    ForEach(entry.rows.prefix(rowLimit)) { row in
                        TaskRowView(row: row, colors: colors, family: family)
                    }
                }
                Spacer(minLength: 0)
                if showsMore {
                    Text("+\(entry.totalCount - min(rowLimit, entry.rows.count)) more")
                        .font(.caption2)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(for: .widget) { colors.background }
        .widgetURL(entry.openURL)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(entry.name)
                .font(family == .systemSmall ? .subheadline.weight(.semibold) : .headline)
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
            if entry.message == nil, entry.totalCount > 0 {
                Text("\(entry.totalCount)")
                    .font(.subheadline)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            Spacer(minLength: 0)
            // A small widget has one tap target (the whole widget); the + needs a medium or large one.
            if family != .systemSmall, let addURL = entry.addURL {
                Link(destination: addURL) {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(colors.onPrimary)
                        .frame(width: 38, height: 24)
                        .background(Capsule().fill(colors.primary))
                        .widgetAccentable()
                }
                .accessibilityLabel("Add task")
            }
        }
    }
}

struct TaskRowView: View {
    let row: WidgetSnapshotRow
    let colors: WidgetColors
    let family: WidgetFamily

    var body: some View {
        HStack(spacing: 8) {
            Button(intent: ToggleTaskIntent(taskId: row.id)) {
                Image(systemName: row.done ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 18))
                    .foregroundStyle(row.done ? colors.done : colors.outline)
                    .frame(width: 24, height: family == .systemLarge ? 28 : 22)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(row.done)
            .accessibilityLabel(row.done ? "Completed" : "Mark complete")

            Link(destination: URL(string: "\(WidgetStore.scheme)://task/\(row.id)")!) {
                HStack(spacing: 6) {
                    if family == .systemLarge {
                        VStack(alignment: .leading, spacing: 0) {
                            title
                            if !row.dueLabel.isEmpty { dueLabel }
                        }
                        Spacer(minLength: 4)
                    } else {
                        title
                        Spacer(minLength: 4)
                        // A small widget has no room for the date next to the title.
                        if family == .systemMedium, !row.dueLabel.isEmpty { dueLabel.fixedSize() }
                    }
                    if row.priority > 0 {
                        PriorityMark(priority: row.priority, color: colors.priority(row.priority))
                    }
                }
            }
        }
    }

    private var title: some View {
        Text(row.title)
            .font(.subheadline)
            .strikethrough(row.done)
            .foregroundStyle(row.done ? colors.onSurfaceVariant : colors.onSurface)
            .lineLimit(1)
    }

    private var dueLabel: some View {
        Text(row.dueLabel)
            .font(.caption2)
            .foregroundStyle(row.overdue ? colors.overdue : colors.onSurfaceVariant)
            .lineLimit(1)
    }
}

// MARK: - Button

/// The row's circle: completes the task. Never reopens one (a second tap on a row that is
/// being completed must not undo it), as on Android. The app stores and sends the completion the
/// next time it runs; until then the row stays hidden.
struct ToggleTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "Complete task"
    static let isDiscoverable = false

    @Parameter(title: "Task")
    var taskId: Int

    init() {}

    init(taskId: Int64) {
        self.taskId = Int(taskId)
    }

    func perform() async throws -> some IntentResult {
        try WidgetStore.record(WidgetAction(kind: WidgetAction.completeTask, taskId: Int64(taskId)))
        return .result()
    }
}
