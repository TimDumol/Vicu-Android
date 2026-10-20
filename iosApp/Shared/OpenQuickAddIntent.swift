import AppIntents
#if VICU_APP
import shared
#endif

/// Opens Vicu with the task entry: the Control Center control (in the widget extension), and the
/// Open quick add shortcut for Siri, Spotlight and the Action Button. Built into the app and the widget
/// extension; with openAppWhenRun iOS opens the app and runs it there.
struct OpenQuickAddIntent: AppIntent {
    static let title: LocalizedStringResource = "Open quick add"
    static let description = IntentDescription("Opens Vicu with a new task.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        #if VICU_APP
        IosApp.shared.openQuickAdd()
        #endif
        return .result()
    }
}
