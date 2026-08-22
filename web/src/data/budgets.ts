/**
 * Budgets. Shared configuration, so reads are open to the household and writes are
 * owner/admin-only under the rules.
 */
import { collection, deleteDoc, doc, onSnapshot, setDoc } from 'firebase/firestore'

import { db } from '../firebase'
import type { Budget } from '../types'
import type { Unsubscribe } from './expenses'

const budgetsCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'budgets')

export function observeBudgets(
  householdId: string,
  onChange: (budgets: Budget[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  return onSnapshot(
    budgetsCollection(householdId),
    (snapshot) =>
      onChange(
        snapshot.docs.map((d) => ({
          id: d.id,
          householdId,
          categoryId: (d.data().categoryId as string) ?? '',
          categoryName: (d.data().categoryName as string) ?? '',
          monthlyLimit: Number(d.data().monthlyLimit ?? 0),
        })),
      ),
    onError,
  )
}

export async function upsertBudget(budget: Budget): Promise<void> {
  await setDoc(doc(budgetsCollection(budget.householdId), budget.id), {
    householdId: budget.householdId,
    categoryId: budget.categoryId,
    categoryName: budget.categoryName,
    monthlyLimit: budget.monthlyLimit,
  })
}

export async function deleteBudget(householdId: string, id: string): Promise<void> {
  await deleteDoc(doc(budgetsCollection(householdId), id))
}
