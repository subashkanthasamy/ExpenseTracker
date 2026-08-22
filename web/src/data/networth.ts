/** Assets and liabilities. Shared configuration: owner/admin writes only. */
import { collection, deleteDoc, doc, onSnapshot, setDoc } from 'firebase/firestore'

import { db } from '../firebase'
import type { Asset, Liability } from '../types'
import type { Unsubscribe } from './expenses'

const assetsCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'assets')

const liabilitiesCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'liabilities')

export function observeAssets(
  householdId: string,
  onChange: (assets: Asset[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  return onSnapshot(
    assetsCollection(householdId),
    (snapshot) =>
      onChange(
        snapshot.docs.map((d) => ({
          id: d.id,
          householdId,
          name: (d.data().name as string) ?? '',
          value: Number(d.data().value ?? 0),
          type: (d.data().type as string) ?? '',
          date: Number(d.data().date ?? 0),
          addedBy: (d.data().addedBy as string) ?? '',
        })),
      ),
    onError,
  )
}

export function observeLiabilities(
  householdId: string,
  onChange: (liabilities: Liability[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  return onSnapshot(
    liabilitiesCollection(householdId),
    (snapshot) =>
      onChange(
        snapshot.docs.map((d) => ({
          id: d.id,
          householdId,
          name: (d.data().name as string) ?? '',
          amount: Number(d.data().amount ?? 0),
          type: (d.data().type as string) ?? '',
          date: Number(d.data().date ?? 0),
          addedBy: (d.data().addedBy as string) ?? '',
        })),
      ),
    onError,
  )
}

export async function upsertAsset(asset: Asset): Promise<void> {
  await setDoc(doc(assetsCollection(asset.householdId), asset.id), {
    name: asset.name,
    value: asset.value,
    type: asset.type,
    date: asset.date,
    addedBy: asset.addedBy,
  })
}

export async function upsertLiability(liability: Liability): Promise<void> {
  await setDoc(doc(liabilitiesCollection(liability.householdId), liability.id), {
    name: liability.name,
    amount: liability.amount,
    type: liability.type,
    date: liability.date,
    addedBy: liability.addedBy,
  })
}

export const deleteAsset = (householdId: string, id: string): Promise<void> =>
  deleteDoc(doc(assetsCollection(householdId), id))

export const deleteLiability = (householdId: string, id: string): Promise<void> =>
  deleteDoc(doc(liabilitiesCollection(householdId), id))
