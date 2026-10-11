import AppIntents

/// Siri, Spotlight and the Action Button: "Add a task in Vicu" opens the task entry.
struct VicuShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenQuickAddIntent(),
            phrases: [
                "Add a task in \(.applicationName)",
                "New \(.applicationName) task",
                "Add to \(.applicationName)",
            ],
            shortTitle: "Add task",
            systemImageName: "plus.circle"
        )
    }
}
