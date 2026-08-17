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
        static let lastPaymentMethod = "last_payment_method"
    }

    /// Last method chosen when adding an expense.
    ///
    /// A device preference, not household data — it never reaches Firestore, so it does not
    /// touch the permission model. Defaults to UPI, the most common instrument for a rupee
    /// household; without this every entry costs a tap on the field users change least.
    static func lastPaymentMethodWire(_ defaults: UserDefaults = .standard) -> String? {
        defaults.string(forKey: Key.lastPaymentMethod)
    }

    static func setLastPaymentMethodWire(_ wire: String, _ defaults: UserDefaults = .standard) {
        defaults.set(wire, forKey: Key.lastPaymentMethod)
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
