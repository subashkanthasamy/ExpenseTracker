import Foundation
import UserNotifications

/// Local notifications for reminders — the iOS counterpart to Android's NotificationHelper.
///
/// Reminders are stored on the device rather than in Firestore on purpose: a notification
/// schedule belongs to the device that shows it, and Android likewise keeps them in Room.
///
/// Repeats use `UNCalendarNotificationTrigger`, so they don't depend on background execution
/// (unlike anything that would need BGTaskScheduler).
@Observable
final class NotificationService {

    enum ReminderKind: String, Codable, CaseIterable {
        case dailyLog
        case bill

        var title: String {
            switch self {
            case .dailyLog: return "Daily expense reminder"
            case .bill: return "Bill reminder"
            }
        }
    }

    struct ScheduledReminder: Identifiable, Codable, Equatable {
        var id: String = UUID().uuidString
        var kind: ReminderKind
        var label: String
        var hour: Int
        var minute: Int
        /// Only used by `.bill` — day of month it falls due.
        var dayOfMonth: Int?
        var amount: Double?
        var isEnabled: Bool = true
    }

    private let defaultsKey = "scheduled_reminders"
    private let defaults: UserDefaults
    private let center = UNUserNotificationCenter.current()

    var reminders: [ScheduledReminder] = []
    var authorizationDenied = false

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.reminders = Self.load(from: defaults, key: defaultsKey)
    }

    // MARK: - Permission

    @discardableResult
    func requestAuthorization() async -> Bool {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            authorizationDenied = !granted
            return granted
        } catch {
            authorizationDenied = true
            return false
        }
    }

    // MARK: - CRUD

    func add(_ reminder: ScheduledReminder) async {
        guard await requestAuthorization() else { return }
        reminders.append(reminder)
        persist()
        await schedule(reminder)
    }

    func remove(_ reminder: ScheduledReminder) {
        reminders.removeAll { $0.id == reminder.id }
        persist()
        center.removePendingNotificationRequests(withIdentifiers: [reminder.id])
    }

    func setEnabled(_ enabled: Bool, for reminder: ScheduledReminder) async {
        guard let index = reminders.firstIndex(where: { $0.id == reminder.id }) else { return }
        reminders[index].isEnabled = enabled
        persist()
        if enabled {
            guard await requestAuthorization() else { return }
            await schedule(reminders[index])
        } else {
            center.removePendingNotificationRequests(withIdentifiers: [reminder.id])
        }
    }

    /// Re-registers everything; safe to call on launch since identifiers are stable.
    ///
    /// Deliberately does **not** request authorization: doing so made the system
    /// notification prompt appear on every launch, which is both startling and against
    /// Apple's guidance to ask in context. Permission is requested when the user actually
    /// adds a reminder; here we only reschedule if it has already been granted.
    func rescheduleAll() async {
        guard !reminders.isEmpty else { return }
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional else {
            authorizationDenied = settings.authorizationStatus == .denied
            return
        }
        for reminder in reminders where reminder.isEnabled {
            await schedule(reminder)
        }
    }

    // MARK: - Scheduling

    private func schedule(_ reminder: ScheduledReminder) async {
        var components = DateComponents()
        components.hour = reminder.hour
        components.minute = reminder.minute
        // Omitting `day` repeats daily; setting it repeats monthly.
        if reminder.kind == .bill, let day = reminder.dayOfMonth {
            components.day = day
        }

        let content = UNMutableNotificationContent()
        content.title = reminder.kind.title
        switch reminder.kind {
        case .dailyLog:
            content.body = "Log today's expenses so your totals stay accurate."
        case .bill:
            if let amount = reminder.amount {
                content.body = "\(reminder.label) — \(formatCurrency(amount)) is due."
            } else {
                content.body = "\(reminder.label) is due."
            }
        }
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: reminder.id,
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
        )
        try? await center.add(request)
    }

    // MARK: - Persistence

    private func persist() {
        if let data = try? JSONEncoder().encode(reminders) {
            defaults.set(data, forKey: defaultsKey)
        }
    }

    private static func load(from defaults: UserDefaults, key: String) -> [ScheduledReminder] {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([ScheduledReminder].self, from: data) else {
            return []
        }
        return decoded
    }
}
