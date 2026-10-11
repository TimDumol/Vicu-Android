import SwiftUI
import shared

@main
struct VicuApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose draws behind the system bars and the keyboard and applies the insets itself.
                .ignoresSafeArea()
                .onOpenURL { url in
                    _ = IosApp.shared.openUrl(url: url.absoluteString)
                }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: IosApp.shared.onActive()
            case .background: IosApp.shared.onBackground()
            default: break
            }
        }
    }
}

/// The shared Compose UI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
