import AppIntents
import SwiftUI
import WidgetKit

/// The Control Center (and Lock Screen, and Action Button) control that opens the task entry:
/// the iOS counterpart of Android's quick-add tile.
@available(iOS 18.0, *)
struct QuickAddControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "QuickAddControl") {
            ControlWidgetButton(action: OpenQuickAddIntent()) {
                Label("Add task", systemImage: "plus.circle")
            }
        }
        .displayName("Add task")
        .description("Opens Vicu with a new task.")
    }
}
