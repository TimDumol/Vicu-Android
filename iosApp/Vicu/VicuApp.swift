import SwiftUI
import shared

@main
struct VicuApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// The window's content. The UI's saved state (screen, open editor, unsaved draft) is kept in scene
/// storage: iOS restores it when it ended the app in the background and drops it when the user
/// closes the app in the app switcher, as it does for every app's restored state.
struct RootView: View {
    @SceneStorage("vicu.uiState") private var savedUiState = ""
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeView(restoredUiState: savedUiState)
            // Compose draws behind the system bars and the keyboard and applies the insets itself.
            .ignoresSafeArea()
            .onOpenURL { url in
                _ = IosApp.shared.openUrl(url: url.absoluteString)
            }
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active:
                    IosApp.shared.onActive()
                case .inactive:
                    savedUiState = IosApp.shared.saveUiState()
                case .background:
                    savedUiState = IosApp.shared.saveUiState()
                    IosApp.shared.onBackground()
                @unknown default:
                    break
                }
            }
    }
}

/// The shared Compose UI, built once with the state restored for this scene.
struct ComposeView: UIViewControllerRepresentable {
    let restoredUiState: String

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(restoredUiState: restoredUiState)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
