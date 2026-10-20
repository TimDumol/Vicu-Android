import AppIntents
import shared

/// "Hey Siri, add a task in Vicu": Siri asks for the task and Vicu adds it without opening, read
/// like quick add ("Call mom tomorrow 3pm #Family"), on a locked phone and in the car too. The
/// task is stored on the device and synced, and Siri says what was added. In the app target only:
/// it runs the shared Kotlin code.
struct AddTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "Add a task"
    static let description = IntentDescription("Adds a task to Vicu. Dates, #project and @labels in the text are read like quick add.")
    static let openAppWhenRun = false
    // It only adds a task and reads nothing back, so it may run while the phone is locked.
    static let authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "Task", requestValueDialog: IntentDialog("What's the task?"))
    var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$text) to Vicu")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let reply = try await IosApp.shared.addTaskFromText(text: text)
        return .result(dialog: IntentDialog(stringLiteral: reply))
    }
}
