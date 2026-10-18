import SwiftUI

extension Color {
    /// A #RRGGBB colour from the app's palette.
    init(hex: String) {
        let value = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}

/// The colours a widget draws with: the app's Vicu palette for the current appearance, or the
/// system's own until the app has written one.
struct WidgetColors {
    let background: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let primary: Color
    let onPrimary: Color
    let outline: Color
    let overdue: Color
    let done: Color
    let priorities: [Color]

    init(_ palette: WidgetPalette?) {
        guard let palette else {
            background = Color(.systemBackground)
            onSurface = .primary
            onSurfaceVariant = .secondary
            primary = .accentColor
            onPrimary = .white
            outline = Color(.tertiaryLabel)
            overdue = .red
            done = .green
            priorities = [.blue, .orange, .orange, .red, .red]
            return
        }
        background = Color(hex: palette.background)
        onSurface = Color(hex: palette.onSurface)
        onSurfaceVariant = Color(hex: palette.onSurfaceVariant)
        primary = Color(hex: palette.primary)
        onPrimary = Color(hex: palette.onPrimary)
        outline = Color(hex: palette.outline)
        overdue = Color(hex: palette.overdue)
        done = Color(hex: palette.done)
        priorities = palette.priorities.map(Color.init(hex:))
    }

    func priority(_ value: Int) -> Color {
        priorities.indices.contains(value - 1) ? priorities[value - 1] : onSurfaceVariant
    }
}

/// The palettes of both appearances, picked by the widget's colour scheme.
struct WidgetPalettes {
    let light: WidgetPalette?
    let dark: WidgetPalette?

    init(_ catalog: WidgetCatalog?) {
        light = catalog?.light
        dark = catalog?.dark
    }

    func colors(_ scheme: ColorScheme) -> WidgetColors {
        WidgetColors(scheme == .dark ? dark : light)
    }
}

/// The priority mark of the design system (section 3): one to three bars for low to high, a
/// filled square with "!" for urgent and do now; nothing for no priority.
struct PriorityMark: View {
    let priority: Int
    let color: Color

    var body: some View {
        Group {
            if priority >= 4 {
                ZStack {
                    RoundedRectangle(cornerRadius: 2.5).fill(color)
                    Text("!").font(.system(size: 10, weight: .heavy)).foregroundStyle(.white)
                }
                .frame(width: 12, height: 12)
            } else {
                HStack(alignment: .bottom, spacing: 1.5) {
                    ForEach(1...3, id: \.self) { bar in
                        RoundedRectangle(cornerRadius: 1)
                            .fill(color.opacity(bar <= priority ? 1 : 0.25))
                            .frame(width: 3, height: CGFloat(2 + bar * 3))
                    }
                }
                .frame(width: 12, height: 12, alignment: .bottom)
            }
        }
        .accessibilityLabel(Self.names[min(priority, 4) - 1])
    }

    static let names = ["Low priority", "Medium priority", "High priority", "Urgent priority"]
}
