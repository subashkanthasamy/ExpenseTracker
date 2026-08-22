/**
 * Expense reads and writes.
 *
 * The read path is the subtle one. `firestore.rules` allows a manager to read every row, and
 * everyone else to read shared rows plus their own. Firestore authorises a `list` by *proving*
 * every document it could return satisfies the rule — and an unfiltered query cannot be proven,
 * so it is **rejected outright, not filtered**. A member issuing one plain query gets an empty
 * list and a permission error, not a subset.
 *
 * So the query shape is chosen by role: one listener for a manager, two constrained listeners
 * merged by document id for everyone else. This mirrors `FirestoreDataSource.observeExpenses`
 * on Android and `FirestoreService.observeExpenses` on iOS.
 */
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  onSnapshot,
  query,
  setDoc,
  where,
  type QuerySnapshot,
} from 'firebase/firestore'

import { db } from '../firebase'
import type { Expense, PaymentWire, ScopeWire } from '../types'
import { millis } from './wire'

const expensesCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'expenses')

/**
 * A snapshot document as an `Expense`.
 *
 * `householdId` is the parent document rather than a stored field, so it is injected here —
 * the shared bridge and the UI both expect a complete record.
 */
function decode(householdId: string, id: string, data: Record<string, unknown>): Expense {
  return {
    id,
    householdId,
    amount: Number(data.amount ?? 0),
    categoryId: (data.categoryId as string) ?? '',
    categoryName: (data.categoryName as string) ?? '',
    date: millis(data.date),
    notes: (data.notes as string) ?? '',
    addedBy: (data.addedBy as string) ?? '',
    addedByName: (data.addedByName as string) ?? '',
    createdAt: millis(data.createdAt),
    updatedAt: millis(data.updatedAt),
    paymentMethod: ((data.paymentMethod as PaymentWire) ?? '') as PaymentWire,
    // Absent means a row written before scope existed. Failing open to 'shared' matches
    // `ExpenseScope.fromWire`: a typo or version skew must not hide a row from the household.
    scope: ((data.scope as ScopeWire) ?? 'shared') as ScopeWire,
  }
}

export type Unsubscribe = () => void

/**
 * Live expenses for a household, newest first.
 *
 * `canReadAll` must come from `can('readAllExpenses', role)` — it selects the query shape, so
 * passing the wrong value does not degrade the result, it empties it.
 */
export function observeExpenses(
  householdId: string,
  canReadAll: boolean,
  uid: string,
  onChange: (expenses: Expense[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  const base = expensesCollection(householdId)

  if (canReadAll) {
    return onSnapshot(
      base,
      (snapshot) => onChange(sortNewestFirst(snapshot.docs.map((d) => decode(householdId, d.id, d.data())))),
      onError,
    )
  }

  // Two listeners, merged by id. A row that is both shared and authored by this user arrives
  // from both queries; keying by id is what makes that idempotent rather than a duplicate.
  const merged = new Map<string, Expense>()
  const seenBy: Array<Set<string>> = [new Set(), new Set()]
  let received = 0

  const handle = (index: number) => (snapshot: QuerySnapshot) => {
    // Rebuild this query's contribution from scratch, so a deletion actually disappears
    // instead of lingering from the previous snapshot.
    const previous = seenBy[index]!
    const current = new Set<string>()
    snapshot.docs.forEach((d) => {
      current.add(d.id)
      merged.set(d.id, decode(householdId, d.id, d.data()))
    })
    previous.forEach((id) => {
      // Dropped from this query, and not held by the other one.
      if (!current.has(id) && !seenBy[1 - index]!.has(id)) merged.delete(id)
    })
    seenBy[index] = current

    received |= 1 << index
    // Emit only once both queries have reported, or the first paint shows a partial list that
    // visibly fills in — which reads as a bug when the missing rows are your own.
    if (received === 0b11) onChange(sortNewestFirst([...merged.values()]))
  }

  const unsubscribeShared = onSnapshot(
    query(base, where('scope', '==', 'shared')),
    handle(0),
    onError,
  )
  const unsubscribeOwn = onSnapshot(
    query(base, where('addedBy', '==', uid)),
    handle(1),
    onError,
  )

  return () => {
    unsubscribeShared()
    unsubscribeOwn()
  }
}

const sortNewestFirst = (expenses: Expense[]): Expense[] =>
  expenses.sort((a, b) => b.date - a.date || b.createdAt - a.createdAt)

/** The fields the mobile clients write, and nothing else. */
function encode(expense: Omit<Expense, 'id' | 'householdId'>) {
  return {
    amount: expense.amount,
    categoryId: expense.categoryId,
    categoryName: expense.categoryName,
    date: expense.date,
    notes: expense.notes,
    addedBy: expense.addedBy,
    addedByName: expense.addedByName,
    createdAt: expense.createdAt,
    updatedAt: expense.updatedAt,
    paymentMethod: expense.paymentMethod,
    // Always written. The read rule tests `scope` literally, so omitting it would make the
    // row unreadable to every member including its author.
    scope: expense.scope,
  }
}

export async function createExpense(
  householdId: string,
  expense: Omit<Expense, 'id' | 'householdId'>,
): Promise<string> {
  const ref = await addDoc(expensesCollection(householdId), encode(expense))
  return ref.id
}

export async function updateExpense(householdId: string, expense: Expense): Promise<void> {
  // `set` rather than `update`, matching the mobile clients: the rules compare the full
  // resulting document, and a partial update would leave `scope` unasserted on legacy rows.
  await setDoc(doc(expensesCollection(householdId), expense.id), encode(expense))
}

export async function deleteExpense(householdId: string, expenseId: string): Promise<void> {
  await deleteDoc(doc(expensesCollection(householdId), expenseId))
}
