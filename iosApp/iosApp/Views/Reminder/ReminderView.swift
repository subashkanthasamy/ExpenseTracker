import SwiftUI

struct ReminderView: View {
    @Bindable var notifications: NotificationService
    @State private var showAddBill = false
    @State private var dailyTime = Calendar.current.date(from: DateComponents(hour: 21, minute: 0)) ?? Date()

    private var dailyReminder: NotificationService.ScheduledReminder? {
        notifications.reminders.first { $0.kind == .dailyLog }
    }

    private var billReminders: [NotificationService.ScheduledReminder] {
        notifications.reminders.filter { $0.kind == .bill }
    }

    var body: some View {
        List {
            if notifications.authorizationDenied {
                Section {
                    Label("Notifications are off for Expense Tracker. Turn them on in Settings.",
                          systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .font(.caption)
                }
            }

            Section("Daily") {
                if let daily = dailyReminder {
                    Toggle(isOn: Binding(
                        get: { daily.isEnabled },
                        set: { newValue in Task { await notifications.setEnabled(newValue, for: daily) } }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Log expenses daily")
                            Text(String(format: "Every day at %02d:%02d", daily.hour, daily.minute))
                                .font(.caption).foregroundStyle(DS.textSecondary)
                        }
                    }
                    Button("Delete", role: .destructive) { notifications.remove(daily) }
                } else {
                    DatePicker("Remind me at", selection: $dailyTime, displayedComponents: .hourAndMinute)
                    Button("Add daily reminder") {
                        let comps = Calendar.current.dateComponents([.hour, .minute], from: dailyTime)
                        Task {
                            await notifications.add(.init(
                                kind: .dailyLog,
                                label: "Log expenses daily",
                                hour: comps.hour ?? 21,
                                minute: comps.minute ?? 0
                            ))
                        }
                    }
                }
            }

            Section("Bills") {
                if billReminders.isEmpty {
                    Text("No bill reminders yet").foregroundStyle(DS.textSecondary).font(.callout)
                }
                ForEach(billReminders) { bill in
                    Toggle(isOn: Binding(
                        get: { bill.isEnabled },
                        set: { newValue in Task { await notifications.setEnabled(newValue, for: bill) } }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(bill.label)
                            Text(billSubtitle(bill)).font(.caption).foregroundStyle(DS.textSecondary)
                        }
                    }
                    .swipeActions {
                        Button("Delete", role: .destructive) { notifications.remove(bill) }
                    }
                }
                Button("Add bill reminder") { showAddBill = true }
            }
        }
        .navigationTitle("Reminders")
        .task { await notifications.rescheduleAll() }
        .sheet(isPresented: $showAddBill) {
            AddBillSheet(notifications: notifications)
        }
    }

    private func billSubtitle(_ bill: NotificationService.ScheduledReminder) -> String {
        let time = String(format: "%02d:%02d", bill.hour, bill.minute)
        let day = bill.dayOfMonth.map { "day \($0)" } ?? "monthly"
        if let amount = bill.amount {
            return "\(formatCurrency(amount)) · \(day) at \(time)"
        }
        return "\(day) at \(time)"
    }
}

private struct AddBillSheet: View {
    @Bindable var notifications: NotificationService
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var amount = ""
    @State private var dayOfMonth = 1
    @State private var time = Calendar.current.date(from: DateComponents(hour: 10, minute: 0)) ?? Date()

    var body: some View {
        NavigationStack {
            Form {
                Section("Bill") {
                    TextField("Bill name", text: $name)
                    TextField("Amount (optional)", text: $amount).keyboardType(.decimalPad)
                }
                Section("When") {
                    Picker("Day of month", selection: $dayOfMonth) {
                        ForEach(1...31, id: \.self) { d in Text("\(d)").tag(d) }
                    }
                    DatePicker("Time", selection: $time, displayedComponents: .hourAndMinute)
                }
            }
            .navigationTitle("Add bill reminder")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        let comps = Calendar.current.dateComponents([.hour, .minute], from: time)
                        Task {
                            await notifications.add(.init(
                                kind: .bill,
                                label: name,
                                hour: comps.hour ?? 10,
                                minute: comps.minute ?? 0,
                                dayOfMonth: dayOfMonth,
                                amount: Double(amount)
                            ))
                            dismiss()
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
