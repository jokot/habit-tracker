import SwiftUI

extension Color {
    // Primary — habit complete / main actions
    static let habitPrimary = Color(light: Color(hex: "2E7D32"), dark: Color(hex: "81C784"))
    static let habitPrimaryContainer = Color(light: Color(hex: "A5D6A7"), dark: Color(hex: "2E7D32"))

    // Secondary — frozen streak (blue)
    static let habitSecondary = Color(light: Color(hex: "1565C0"), dark: Color(hex: "64B5F6"))

    // Error — broken streak (red)
    static let habitError = Color(light: Color(hex: "C62828"), dark: Color(hex: "EF9A9A"))

    // Surface
    static let habitBackground = Color(light: .white, dark: Color(hex: "121212"))
    static let habitSurface = Color(light: .white, dark: Color(hex: "121212"))
    static let habitSurfaceVariant = Color(light: Color(hex: "EEEEEE"), dark: Color(hex: "2C2C2C"))
    static let habitOutline = Color(light: Color(hex: "757575"), dark: Color(hex: "9E9E9E"))

    // Streak semantic — mirrors Android streak tokens
    static let streakComplete = Color(light: Color(hex: "2E7D32"), dark: Color(hex: "81C784"))
    static let streakFrozen = Color(light: Color(hex: "1565C0"), dark: Color(hex: "64B5F6"))
    static let streakBroken = Color(light: Color(hex: "C62828"), dark: Color(hex: "EF9A9A"))
    static let streakEmpty = Color(light: Color(hex: "EEEEEE"), dark: Color(hex: "2C2C2C"))
    static let streakTodayOutline = Color(light: Color(hex: "757575"), dark: Color(hex: "9E9E9E"))
}

private extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255
        let g = Double((int >> 8) & 0xFF) / 255
        let b = Double(int & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }

    init(light: Color, dark: Color) {
        self.init(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(dark)
                : UIColor(light)
        })
    }
}
