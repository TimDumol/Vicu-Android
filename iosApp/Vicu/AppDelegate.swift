import UIKit
import WidgetKit
import shared

/// Starts the shared code before the first scene: iOS also launches the app without a scene
/// (a notification action, a background task), and those need it running too.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosApp.shared.start(widgetBridge: WidgetBridge())
        return true
    }

    /// Puts [SceneDelegate] next to SwiftUI's own, for the home-screen quick action.
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

/// The home-screen quick action (Info.plist UIApplicationShortcutItems): Add task opens the
/// task entry, whether the app was running or not.
final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    static let addTask = "com.rendyhd.vicu.add"

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem {
            _ = handle(item)
        }
    }

    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(handle(shortcutItem))
    }

    private func handle(_ item: UIApplicationShortcutItem) -> Bool {
        guard item.type == Self.addTask else { return false }
        IosApp.shared.openQuickAdd()
        return true
    }
}

/// WidgetKit for the shared code, which writes the widget snapshots and then asks for a reload.
private final class WidgetBridge: NSObject, AppleWidgetBridge {
    func reloadAllTimelines() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
