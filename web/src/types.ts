/**
 * TypeScript shapes of the Firestore documents.
 *
 * These mirror what the Android and iOS clients read and write, field for field. The wire
 * format is fixed by those two clients, so nothing here is a free choice:
 *
 *  - **Time is epoch milliseconds, never a Firestore `Timestamp`.** Writing a `Timestamp`
 *    crashed Android with "Field 'date' is not a java.lang.Number", and in reverse decoded
 *    every Android-written date as "now". Both platforms read either form for compatibility,
 *    but all three must only ever write millis.
 *  - **`householdId` is the parent document, not a field** on expenses and categories. It is
 *    injected when a snapshot is decoded so the shared bridge sees a complete record.
 *  - **`scope` is required on every expense.** The security rule tests it literally, so a row
 *    without it is invisible to members rather than erroring.
 */

/** Role wire values. `roles.<uid>` stores these; `none` is "not in this household". */
export type RoleWire = 'admin' | 'owner' | 'member' | 'guest' | 'none'

/** `''` is UNSPECIFIED — every expense written before payment methods existed. */
export type PaymentWire = '' | 'cash' | 'upi' | 'credit_card'

export type ScopeWire = 'shared' | 'personal'

export type DateRangeWire = 'all' | 'this_month' | 'last_month' | 'this_year'

export interface Expense {
  id: string
  householdId: string
  amount: number
  categoryId: string
  categoryName: string
  date: number
  notes: string
  addedBy: string
  addedByName: string
  createdAt: number
  updatedAt: number
  paymentMethod: PaymentWire
  scope: ScopeWire
}

export interface Category {
  id: string
  householdId: string
  name: string
  icon: string
  /** ARGB as written by the mobile clients, e.g. 0xFF4CAF50 as a plain number. */
  color: number
  isPreset: boolean
}

export interface Household {
  id: string
  name: string
  memberUids: string[]
  ownerUid: string
  /** uid -> 'admin' | 'member' | 'guest'. Absent means a pre-roles member, i.e. member. */
  roles: Record<string, string>
  inviteCode: string
  createdAt: number
  presetVersion: number
}

export interface Budget {
  id: string
  householdId: string
  categoryId: string
  categoryName: string
  monthlyLimit: number
}

export interface SavingsGoal {
  id: string
  householdId: string
  name: string
  targetAmount: number
  currentAmount: number
  icon: string
  targetDate: number | null
  createdAt: number
}

/** Stored as the enum ordinal, which is what all three clients read. */
export type RecurringFrequency = 0 | 1 | 2 | 3

export interface RecurringExpense {
  id: string
  householdId: string
  amount: number
  categoryId: string
  categoryName: string
  notes: string
  addedBy: string
  addedByName: string
  frequency: RecurringFrequency
  dayOfWeek: number | null
  dayOfMonth: number | null
  monthOfYear: number | null
  startDate: number
  endDate: number | null
  lastGeneratedDate: number | null
  isActive: boolean
  paymentMethod: PaymentWire
  createdAt: number
}

export interface Asset {
  id: string
  householdId: string
  name: string
  value: number
  type: string
  date: number
  addedBy: string
}

export interface Liability {
  id: string
  householdId: string
  name: string
  amount: number
  type: string
  date: number
  addedBy: string
}

export interface FilterCriteria {
  searchQuery: string
  personFilter: string | null
  categoryFilter: string | null
  paymentMethodFilter: PaymentWire | null
  dateRange: DateRangeWire
}

export const EMPTY_CRITERIA: FilterCriteria = {
  searchQuery: '',
  personFilter: null,
  categoryFilter: null,
  paymentMethodFilter: null,
  dateRange: 'all',
}

export interface FilterOption {
  id: string
  label: string
}

export interface PersonSpending {
  userId: string
  name: string
  amount: number
  share: number
}

export interface SpendingSlice {
  id: string
  label: string
  amount: number
  share: number
}
