import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Start DocTrack's local database, reminder sync and notification delegate early.
        MainViewControllerKt.doInitDocTrack()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
