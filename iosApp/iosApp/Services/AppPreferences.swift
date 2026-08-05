import Foundation
import SwiftUI
import Shared

/// iOS counterpart to Android's DataStore-backed preferences.
///
/// Theme values deliberately reuse `ThemePreferences.THEME_*` from the shared module so
/// both platforms persist the same integers and can't drift apart.
@Observable
final class AppPreferences {

    private enum Key {
        static let themeMode = "theme_mode"
        static let biometricEnabled = "biometric_enabled"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // `object(forKey:)` distinguishes "unset" from 0, which is a valid theme value.
        self.themeMode = (defaults.object(forKey: Key.themeMode) as? Int)
            ?? Int(ThemePreferencesCompanion.shared.THEME_SYSTEM)
        self.biometricEnabled = defaults.bool(forKey: Key.biometricEnabled)
    }

    var themeMode: Int {
        didSet { defaults.set(themeMode, forKey: Key.themeMode) }
    }

    var biometricEnabled: Bool {
        didSet { defaults.set(biometricEnabled, forKey: Key.biometricEnabled) }
    }

    /// nil means "follow the system", matching Android's THEME_SYSTEM.
    var colorScheme: ColorScheme? {
        switch Int32(themeMode) {
        case ThemePreferencesCompanion.shared.THEME_LIGHT: return .light
        case ThemePreferencesCompanion.shared.THEME_DARK: return .dark
        default: return nil
        }
    }

    var themeLabel: String {
        switch Int32(themeMode) {
        case ThemePreferencesCompanion.shared.THEME_LIGHT: return "Light"
        case ThemePreferencesCompanion.shared.THEME_DARK: return "Dark"
        default: return "System"
        }
    }
}
