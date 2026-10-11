import SwiftUI
import UIKit

/// The share sheet entry point: a small native sheet with the title and notes filled in from
/// what was shared, and Add, which leaves it for the app to turn into a task (IosShareInbox).
final class ShareViewController: UIViewController {
    private let model = ShareModel()

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: ShareView(
            model: model,
            onCancel: { [weak self] in self?.cancel() },
            onAdd: { [weak self] in self?.add() }
        ))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)

        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        Task { await model.load(items) }
    }

    private func cancel() {
        model.discard()
        extensionContext?.cancelRequest(withError: CocoaError(.userCancelled))
    }

    private func add() {
        do {
            try model.save()
            extensionContext?.completeRequest(returningItems: nil)
        } catch {
            model.error = "Could not save it for Vicu: \(error.localizedDescription)"
        }
    }
}

struct ShareView: View {
    @ObservedObject var model: ShareModel
    let onCancel: () -> Void
    let onAdd: () -> Void

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Add to Vicu")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", action: onCancel)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add", action: onAdd)
                            .fontWeight(.semibold)
                            .disabled(!model.canAdd)
                    }
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.loading {
            ProgressView()
        } else if !model.signedIn {
            ContentUnavailableView(
                "Sign in to Vicu",
                systemImage: "person.crop.circle.badge.exclamationmark",
                description: Text("Open Vicu and sign in to your Vikunja server, then share again.")
            )
        } else {
            Form {
                Section {
                    TextField("Title", text: $model.title, axis: .vertical)
                        .lineLimit(1...4)
                }
                Section("Notes") {
                    TextField("Notes", text: $model.notes, axis: .vertical)
                        .lineLimit(2...8)
                }
                if !model.files.isEmpty {
                    Section("Attachments") {
                        ForEach(model.files, id: \.self) { name in
                            Label(name, systemImage: "paperclip")
                                .lineLimit(1)
                        }
                    }
                }
                Section {
                    if let error = model.error {
                        Text(error).foregroundStyle(.red)
                    }
                } footer: {
                    Text("It goes to your Inbox. Vicu adds it the next time it opens or refreshes in the background\(model.files.isEmpty ? "" : ", with a connection for the attachments").")
                }
            }
        }
    }
}
