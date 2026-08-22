/**
 * Categories, including the preset top-up.
 *
 * Seeding is **owner/admin-only** under the rules: `manageSharedConfig`. A member opening the
 * app first simply sees whatever categories exist; the top-up happens the next time a manager
 * opens it. Attempting it as a member would be denied, so it is not attempted.
 */
import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  onSnapshot,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore'

import { db } from '../firebase'
import { categoryPresetId, categoryPresets, categoryPresetVersion } from '../shared'
import type { Category, Household } from '../types'
import type { Unsubscribe } from './expenses'

const categoriesCollection = (householdId: string) =>
  collection(db, 'households', householdId, 'categories')

function decode(householdId: string, id: string, data: Record<string, unknown>): Category {
  return {
    id,
    householdId,
    name: (data.name as string) ?? '',
    icon: (data.icon as string) ?? '',
    color: Number(data.color ?? 0),
    isPreset: data.isPreset === true,
  }
}

export function observeCategories(
  householdId: string,
  onChange: (categories: Category[]) => void,
  onError: (error: Error) => void,
): Unsubscribe {
  return onSnapshot(
    categoriesCollection(householdId),
    (snapshot) =>
      onChange(
        snapshot.docs
          .map((d) => decode(householdId, d.id, d.data()))
          .sort((a, b) => a.name.localeCompare(b.name)),
      ),
    onError,
  )
}

export async function upsertCategory(
  householdId: string,
  category: Omit<Category, 'householdId'>,
): Promise<void> {
  await setDoc(doc(categoriesCollection(householdId), category.id), {
    name: category.name,
    icon: category.icon,
    color: category.color,
    isPreset: category.isPreset,
  })
}

export async function deleteCategory(householdId: string, categoryId: string): Promise<void> {
  await deleteDoc(doc(categoriesCollection(householdId), categoryId))
}

/**
 * Adds any preset the household is missing, then stamps the catalogue version.
 *
 * Two details carry the weight:
 *
 * - **Matching is by name, including against custom categories.** A household where someone
 *   hand-created "Medical" must not end up with two of them.
 * - **The version stamp is what makes this one-shot.** Matching by name alone would resurrect
 *   a preset the owner deliberately deleted, on every single load — seeding fighting the user,
 *   which is worse than a missing category. So the whole pass is skipped once the household's
 *   `presetVersion` has caught up.
 *
 * Ids are `preset_{householdId}_{slug}`, the scheme Android already uses, so a household seeded
 * by any of the three clients converges on the same documents rather than duplicating them.
 */
export async function topUpPresetCategories(household: Household): Promise<number> {
  if (household.presetVersion >= categoryPresetVersion()) return 0

  const existing = await getDocs(categoriesCollection(household.id))
  const haveNames = new Set(
    existing.docs.map((d) => String(d.data().name ?? '').trim().toLowerCase()),
  )

  const missing = categoryPresets().filter(
    (preset) => !haveNames.has(preset.name.trim().toLowerCase()),
  )

  if (missing.length > 0) {
    const batch = writeBatch(db)
    missing.forEach((preset) => {
      batch.set(doc(categoriesCollection(household.id), categoryPresetId(household.id, preset.name)), {
        name: preset.name,
        icon: preset.iconKey,
        color: preset.color,
        isPreset: true,
      })
    })
    await batch.commit()
  }

  // Stamped even when nothing was missing, so the read above happens once rather than on
  // every load. Safe as an update: the manager branch of the household rule does not use
  // `hasOnly`, so an unasserted extra field passes — but note it cannot be written at create
  // time, where the rule *does* use `hasOnly`.
  await updateDoc(doc(db, 'households', household.id), {
    presetVersion: categoryPresetVersion(),
  })

  return missing.length
}
