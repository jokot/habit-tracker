import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color.habitBackground.ignoresSafeArea()
            Text("Habit Tracker — Phase 1 ✓")
                .font(HabitFont.titleLarge)
                .foregroundColor(.habitPrimary)
                .padding(HabitSpacing.xl)
        }
    }
}

#Preview {
    ContentView()
}
