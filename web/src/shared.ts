/**
 * Typed facade over the Kotlin/JS shared module.
 *
 * The generated `.d.ts` types every record as `any`, because the bridge passes plain JS
 * objects across the boundary (Kotlin/JS cannot export a `commonMain` data class, and `Long`
 * is not an exportable type at all — see `WebBridge.kt`). This module is where those `any`s
 * become the interfaces in `./types`, so nothing downstream has to.
 *
 * Everything re-exported here is the *same implementation* Android and iOS run. The point is
 * that the access rules, the filter predicate, the money formatting and the category catalogue
 * cannot drift between the three clients — there is one copy, in `shared/`.
 */
import * as kt from 'expensetracker-shared'

import type {
  Category,
  Expense,
  FilterCriteria,
  FilterOption,
  Household,
  PaymentWire,
  PersonSpending,
  RoleWire,
  ScopeWire,
  SpendingSlice,
} from './types'

// ---------------------------------------------------------------------------
// Access control
//
// This mirrors `firestore.rules` and does not enforce it. The browser talks to Firestore with
// the user's own credentials, so every one of these calls is a hint for the UI — the rules are
// the only thing that actually stops a request.
// ---------------------------------------------------------------------------

/** Capabilities the rules recognise. A name not in this union has no rules counterpart. */
export type Capability =
  | 'viewHousehold'
  | 'deleteHousehold'
  | 'renameHousehold'
  | 'manageMembers'
  | 'manageInviteCode'
  | 'manageSharedConfig'
  | 'addExpense'
  | 'readAllExpenses'
  | 'leaveHousehold'

/** Role of `uid`, where `isAdmin` is the `admin` custom claim from the ID token. */
export const roleOf = (household: Household, uid: string, isAdmin: boolean): RoleWire =>
  kt.roleOf(household, uid, isAdmin) as RoleWire

export const can = (capability: Capability, role: RoleWire): boolean =>
  kt.can(capability, role)

export const canEditExpense = (role: RoleWire, expense: Expense, uid: string): boolean =>
  kt.canEditExpense(role, expense.addedBy, uid)

export const roleLabel = (role: RoleWire): string => kt.roleLabel(role)

export const roleDescription = (role: RoleWire): string => kt.roleDescription(role)

/**
 * Rows that count toward the household's *shared* figures.
 *
 * Every number presented as the household's total goes through this. A member cannot see
 * peers' personal rows, so including personal rows would give the owner and a member different
 * answers for the same label.
 */
export const sharedOnly = (expenses: Expense[]): Expense[] =>
  kt.sharedOnly(expenses) as Expense[]

// ---------------------------------------------------------------------------
// Filtering — the same predicate the Android and iOS expense screens use, so a search that
// matches on one platform matches on all three (including matching on the amount).
// ---------------------------------------------------------------------------

export const filterExpenses = (
  expenses: Expense[],
  criteria: FilterCriteria,
  now: number = Date.now(),
): Expense[] => kt.filterExpenses(expenses, criteria, now) as Expense[]

export const categoryOptions = (expenses: Expense[]): FilterOption[] =>
  kt.categoryOptions(expenses) as FilterOption[]

export const personOptions = (expenses: Expense[]): FilterOption[] =>
  kt.personOptions(expenses) as FilterOption[]

export const dateRangeOptions = (): FilterOption[] =>
  kt.dateRangeOptions() as FilterOption[]

// ---------------------------------------------------------------------------
// Insights
// ---------------------------------------------------------------------------

export const personSplit = (expenses: Expense[]): PersonSpending[] =>
  kt.personSplit(expenses) as PersonSpending[]

export const paymentSplit = (expenses: Expense[]): SpendingSlice[] =>
  kt.paymentSplit(expenses) as SpendingSlice[]

// ---------------------------------------------------------------------------
// Money
//
// Deliberately not `Intl.NumberFormat`: the abbreviated forms are lakh and crore, which no
// locale gives you, and the grouping must match the mobile apps exactly.
// ---------------------------------------------------------------------------

/** Full precision, e.g. `₹1,23,456.78`. */
export const money = (amount: number): string => kt.formatMoney(amount)

/** Abbreviated, e.g. `₹1.2L`. For headline figures and axis labels. */
export const moneyShort = (amount: number): string => kt.formatMoneyShort(amount)

// ---------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------

export interface CategoryPreset {
  name: string
  iconKey: string
  /** ARGB as the Firestore document stores it, e.g. 0xFF4CAF50. */
  color: number
  /** The same colour as CSS `#rrggbb`. */
  colorHex: string
}

export const categoryPresets = (): CategoryPreset[] =>
  kt.categoryPresets() as CategoryPreset[]

export const categoryPresetVersion = (): number => kt.categoryPresetVersion()

export const categoryPresetId = (householdId: string, name: string): string =>
  kt.categoryPresetId(householdId, name)

/**
 * Material Symbols ligature name for a category.
 *
 * The stored `icon` is trusted only when it is already a canonical key; an emoji from an
 * older iOS-seeded household, or a blank, falls back to deriving from the name. The key is
 * the Material Symbols name verbatim, so it can be rendered as a ligature with no mapping.
 */
export const categoryIconKey = (icon: string | null | undefined, name: string): string =>
  kt.categoryIconKey(icon ?? null, name)

/** ARGB number as stored by the mobile clients, as a CSS colour. */
export const categoryColor = (argb: number): string => kt.categoryColor(argb)

export const colorOf = (category: Category): string => categoryColor(category.color)

// ---------------------------------------------------------------------------
// Payment methods and scope
// ---------------------------------------------------------------------------

export interface PaymentMethodOption {
  wire: PaymentWire
  label: string
  emoji: string
}

/** Selectable methods in picker order. UNSPECIFIED is not offered — it is decode-only. */
export const paymentMethods = (): PaymentMethodOption[] =>
  kt.paymentMethods() as PaymentMethodOption[]

export const paymentMethodLabel = (wire: PaymentWire): string => kt.paymentMethodLabel(wire)

export interface ScopeOption {
  wire: ScopeWire
  label: string
}

export const expenseScopes = (): ScopeOption[] => kt.expenseScopes() as ScopeOption[]

// ---------------------------------------------------------------------------
// Budgets and goals — the thresholds live on the shared models, so an "80% warning" means
// the same thing on all three clients.
// ---------------------------------------------------------------------------

export interface BudgetProgress {
  percentage: number
  status: 'ok' | 'warning' | 'exceeded'
}

export const budgetProgress = (spent: number, monthlyLimit: number): BudgetProgress =>
  kt.budgetProgress(spent, monthlyLimit) as BudgetProgress

export interface GoalProgress {
  /** 0..1, already clamped. */
  progress: number
  remaining: number
  /** null when there is no target date, it has passed, or nothing is left to save. */
  monthlyNeeded: number | null
}

export const goalProgress = (
  currentAmount: number,
  targetAmount: number,
  targetDate: number | null,
): GoalProgress =>
  kt.goalProgress(currentAmount, targetAmount, targetDate) as GoalProgress

// ---------------------------------------------------------------------------
// SMS paste-to-parse
//
// The browser cannot read an SMS inbox, exactly as iOS cannot. The *parser* is shared and
// works fine here, so pasting a bank alert is the web substitute for Android's automatic
// import — same heuristics, same category matching.
// ---------------------------------------------------------------------------

export interface ParsedSms {
  amount: number
  merchant: string | null
  paymentMethod: PaymentWire
  categoryName: string | null
  rawMessage: string
}

/** Parsed debit, or null when the text is not a recognisable transaction alert. */
export const parseSms = (
  sender: string,
  body: string,
  received: number = Date.now(),
): ParsedSms | null => (kt.parseSms(sender, body, received) as ParsedSms | null) ?? null
