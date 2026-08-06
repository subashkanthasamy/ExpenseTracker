import SwiftUI
import FirebaseCore
import GoogleSignIn
import Shared

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        // Smoke test that the KMP Shared framework is linked and callable at runtime.
        // Top-level Kotlin functions are exported per-file, hence the Platform_iosKt wrapper.
        print("Shared framework linked — platform: \(Platform_iosKt.getPlatformName())")
        return true
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Completes the Google Sign-In OAuth redirect (CFBundleURLTypes in Info.plist).
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
