import UIKit
import shared

/// Starts the shared code before the first scene: iOS also launches the app without a scene
/// (a notification action, a background task), and those need it running too.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosApp.shared.start()
        return true
    }
}
