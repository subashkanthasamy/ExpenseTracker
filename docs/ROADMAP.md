# Expense Tracker — Feature roadmap

A backlog of candidate features, grounded in the current codebase, the platform-parity gaps
in `CLAUDE.md`, and bugs found during the September 2026 UI and copy review.

**Size:** S = a day or two · M = about a week · L = longer. Sizes cover all three clients
(Android, iOS, web) unless a line says otherwise.

---

## Fix first — known bugs

These were found during the review and are not features. They should land before new work.

*All seven fixed on 2026-09-26 (not yet released). Along the way: iOS now falls back to the
device passcode when Face ID is locked out, both mobile apps import any of the three clients'
CSV exports by column name, and household deletion cascades identically everywhere — see
`CLAUDE.md`.*

- [x] **Biometric lock can be bypassed (Android).** In `MainActivity`, the error path —
      including cancel — still opens the app.
- [x] **CSV export and import don't match (Android).** Export writes 6 columns (including
      Payment Method); import expects exactly 5. An exported file can't be re-imported.
- [x] **Silent failures (iOS).** Errors from changing a role, removing a member, deleting a
      household and adding a category are set but never displayed.
- [x] **Dead controls (Android).** The notification bell on Insights and Expenses has an empty
      `onClick`. The Expense/Income toggle on Add expense (`isExpenseMode`) is never read.
- [x] **Export failures are never shown (Android).** They go to `dummyDataMessage`, which no
      screen displays.
- [x] **Misleading dashboard badges (Android).** The insight badges were picked by whether a
      category is over 25% of spending, with no budget involved. They were relabelled during
      the copy pass, but the logic still needs a look.
- [x] **Household deletion differs by platform.** Android deletes expenses, categories, assets
      and liabilities; iOS and the web delete only the household document. Neither deletes
      budgets, savings goals or recurring expenses. See *Server-side household actions* below.

---

## Top priorities

### 1. Settle up between members — M
Track who owes whom and record a settlement when someone pays back.
- The split calculators already live in `shared/`, so most of the maths is written.
- Needs a settlement record in Firestore, a balance summary per member, and a "Settle up"
  action.
- Personal expenses stay out of every balance, as they are out of every household total today.

### 2. Income and monthly cash flow — M
- Android's Add expense screen already has an Income toggle that does nothing.
- Add income records, then show savings rate and money left this month on the dashboard.
- New shared model, security rules, and a web and iOS entry point.

### 3. Budget alerts that actually fire — M
- `NotificationHelper.showBudgetAlertNotification` has no callers today.
- A server-side check (Cloud Functions plus FCM and Web Push) would alert every member on
  every device, including the web, which has no notifications at all today.
- The Android reminder screen already has the settings; they need to do something.

### 4. In-app account deletion — S–M
- Google Play and the App Store both require it for apps that allow sign-up. Without it, a
  store review can reject the app.
- Decide what happens to that person's expenses, and to a household they own. Ownership is
  deliberately not transferable today, so this needs a product decision first.

### 5. Server-side household actions — M–L
Move sensitive operations into Cloud Functions:
- **Join:** make the function the only writer of `memberUids` and `roles`, then set the rules'
  join branch to `if false`. This closes the gap where a code shared on by a member keeps
  working until rotated.
- **Delete household:** cascade every subcollection, identically on all platforms.
- **Recurring generation:** one server scheduler instead of the mobile clients racing on
  `lastGeneratedDate`. This also lets the web support recurring expenses fully.

---

## Money features

- [ ] **Bills and EMI calendar — M.** Shared due dates with paid and overdue states. Today
      bill reminders are device-local only.
- [ ] **Trips and events — M.** Group expenses under something like "Goa trip", with its own
      total and split.
- [ ] **Receipt photos on expenses — S–M.** Store the scanned image (Firebase Storage) with the
      expense, not just the text read from it.
- [ ] **Tags and saved filters — S.** For example `#office`, or a saved "Food this month" view.
- [ ] **Per-member allowances — S–M.** A monthly budget per person, alongside per-category
      budgets.
- [ ] **Tax helper — M.** Tag expenses for Indian tax deductions (80C, 80D) and see a yearly
      total.

## Insights

- [ ] **Unusual-spending alerts — M.** For example "Food is 3× your usual this week".
- [ ] **Month-end forecast everywhere — S.** Android already projects month-end spending; add
      it to iOS and the web.
- [ ] **Monthly report and year in review — M.** A PDF or email summary, with a year-on-year
      comparison.
- [ ] **Finish the financial coach — L.** It is built but behind a flag that's off, and its
      replies are canned (the "afford" answer always says yes). Answer from the household's
      real data, for example with the Claude API.

## Household collaboration

- [ ] **Activity feed — S–M.** "Ravi added ₹380 · Food".
- [ ] **Invite by link or QR code — S.** Instead of typing a 6-character code.
- [ ] **Comments and approvals — M.** Comments on an expense, and an optional "approve before
      it counts" step for large ones.

## Getting data in

- [ ] **Voice entry and receipt scanning on the web — M.** Use the browser's built-in speech
      recognition and in-browser text recognition. The shared parsers (`VoiceExpenseParser`,
      `ReceiptTextParser`) already run on the web through Kotlin/JS.
- [ ] **iOS Share Extension — M.** Share a bank SMS from Messages straight into the app. This
      is the closest to automatic SMS import that iOS allows.
- [ ] **Bank-account sync — L.** Import transactions through India's Account Aggregator system.
      Needs a partner provider.

## Platform

- [ ] **Installable web app — S.** Add a manifest and service worker, so it can be added to the
      home screen. The Firestore offline cache is already enabled.
- [ ] **Home-screen widgets and shortcuts — M.** Quick add and "spent today" on Android and iOS.
- [ ] **Tamil and Hindi — M–L.** Every UI string is hard-coded today, so they must first move
      into resources: `strings.xml` on Android, `Localizable.strings` on iOS, a message
      catalogue on the web.
- [ ] **One CSV format everywhere — S.** The same columns on all three clients, so a backup made
      on one restores on any other. Also fixes the Android mismatch above.
- [ ] **Close the parity gaps — M each.**
  - iOS: phone (OTP) sign-in, demo mode, offline cache.
  - Web: reminders and notifications, receipt scanning, voice entry.
- [ ] **Localised iOS permission prompts — S.** Camera, microphone, photos, speech and Face ID
      prompts live as `INFOPLIST_KEY_*` build settings in `project.pbxproj` and weren't covered
      by the copy pass. Suggested wording:
  - "Expense Tracker uses the camera to scan receipts."
  - "Face ID keeps Expense Tracker locked until you unlock it."
  - "Expense Tracker uses the microphone so you can add expenses by voice."
  - "Expense Tracker reads photos you choose so it can scan receipts."
  - "Speech recognition turns what you say into an expense amount."

---

## Open product decisions

Decisions raised during the review that block or shape items above:

1. **Tab names.** Android and iOS now say Dashboard / Expenses / Net worth; the design PDFs
   call two of these "Home Dashboard" and "Timeline". Keep or revert?
2. **Two insights screens on Android.** "Insights" (formerly Smart Insights) and "Analytics".
   Merge them, or keep both with clearer roles?
3. **Firebase error text.** Every client still appends Firebase's own message after the
   friendly sentence. Map each error code to our own wording instead?
4. **Empty-state prompts for people who can't act.** "Add your first expense" is shown to
   guests, and "Tap + …" to members who have no + button.
5. **Account deletion and ownership.** What happens to an owner's household when the owner
   deletes their account, given ownership can't be transferred?
