/** Savings goals. Shared configuration: owner/admin writes only. */
import { collection, deleteDoc, doc, onSnapshot, setDoc } from 'firebase/firestore'

import { db } from '../firebase'
import type { SavingsGoal } from '../types'
import type { Unsubscribe } from './expenses'
import { millis, millisOrNull } from './wire'

const goalsCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'savingsGoals')

export function observeGoals(
  householdId: string,
  onChange: (goals: SavingsGoal[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  return onSnapshot(
    goalsCollection(householdId),
    (snapshot) =>
      onChange(
        snapshot.docs
          .map((d) => {
            const data = d.data()
            return {
              id: d.id,
              householdId,
              name: (data.name as string) ?? '',
              targetAmount: Number(data.targetAmount ?? 0),
              currentAmount: Number(data.currentAmount ?? 0),
              icon: (data.icon as string) ?? '🎯',
              // Optional on the wire — the mobile clients omit the key rather than writing null.
              targetDate: millisOrNull(data.targetDate),
              createdAt: millis(data.createdAt),
            }
          })
          .sort((a, b) => a.createdAt - b.createdAt),
      ),
    onError,
  )
}

export async function upsertGoal(goal: SavingsGoal): Promise<void> {
  // `targetDate` is omitted rather than written as null, matching the mobile clients.
  const data: Record<string, unknown> = {
    householdId: goal.householdId,
    name: goal.name,
    targetAmount: goal.targetAmount,
    currentAmount: goal.currentAmount,
    icon: goal.icon,
    createdAt: goal.createdAt,
  }
  if (goal.targetDate != null) data.targetDate = goal.targetDate
  await setDoc(doc(goalsCollection(goal.householdId), goal.id), data)
}

export async function deleteGoal(householdId: string, id: string): Promise<void> {
  await deleteDoc(doc(goalsCollection(householdId), id))
}
