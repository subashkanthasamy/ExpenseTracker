/**
 * Household lifecycle and membership.
 *
 * Every write here is shaped by `firestore.rules`, and the constraints are not obvious:
 *
 * - **`memberUids` is an index, `roles` is the authority, and the rules assert they agree**:
 *   `memberUids.toSet() == roles.keys().toSet().union([ownerUid])`. Any manager update that
 *   leaves them inconsistent is rejected, so `normalisedRoles` rebuilds the map on every such
 *   write — which also self-heals households predating roles.
 * - **Joining must write both keys in one update.** The join branch requires `memberUids` to
 *   grow by exactly one and `roles.<uid>` to be exactly `'member'`; touching only one is denied.
 * - **`ownerUid` is immutable and there is no ownership transfer.** Deliberate: it would let a
 *   compromised session hand the household away permanently.
 * - **Joining resolves `inviteCodes/{code}`, never a query on `households`.** Querying
 *   households by invite code would require every household to be readable by any signed-in
 *   user, which allows enumerating them all.
 */
import {
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
  documentId,
} from 'firebase/firestore'

import { db } from '../firebase'
import type { Household, RoleWire } from '../types'

/** Same alphabet and length as the mobile clients, so codes are indistinguishable. */
function generateInviteCode(): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  const bytes = new Uint8Array(6)
  crypto.getRandomValues(bytes)
  return [...bytes].map((b) => alphabet[b % alphabet.length]!).join('')
}

/**
 * `roles` containing exactly the non-owner members.
 *
 * The owner is deliberately absent: the rules union the owner back in, so storing a role for
 * them is redundant and has in practice produced stray `roles[owner] = 'admin'` entries.
 * An existing member with no entry becomes `member`, which is how the rules and both mobile
 * clients already read an absent role.
 */
function normalisedRoles(
  household: Household,
  overrides: { memberUids?: string[]; setRole?: { uid: string; role: RoleWire } } = {},
): Record<string, string> {
  const owner = household.ownerUid || household.memberUids[0] || ''
  const members = overrides.memberUids ?? household.memberUids
  const roles: Record<string, string> = {}
  members.forEach((uid) => {
    if (uid === owner) return
    roles[uid] = household.roles[uid] ?? 'member'
  })
  if (overrides.setRole && overrides.setRole.uid !== owner) {
    roles[overrides.setRole.uid] = overrides.setRole.role
  }
  return roles
}

export interface CreatedHousehold {
  id: string
  inviteCode: string
}

/**
 * Creates a household owned by `uid`.
 *
 * The household document is written first and the invite-code lookup second, because the
 * lookup's create rule requires the author to already be a manager of the household it points
 * at — which is only true once the household exists.
 */
export async function createHousehold(name: string, uid: string): Promise<CreatedHousehold> {
  const inviteCode = generateInviteCode()
  const ref = doc(collection(db, 'households'))

  await setDoc(ref, {
    name: name.trim(),
    ownerUid: uid,
    memberUids: [uid],
    // Empty at creation: the rules require the creator to grant nobody else a role.
    roles: {},
    inviteCode,
    createdAt: Date.now(),
  })

  await publishInviteCode(ref.id, name.trim(), inviteCode)
  return { id: ref.id, inviteCode }
}

async function publishInviteCode(
  householdId: string,
  householdName: string,
  inviteCode: string,
): Promise<void> {
  await setDoc(doc(db, 'inviteCodes', inviteCode), { householdId, householdName })
}

/**
 * Publishes the lookup document if it is missing, so an older household can be joined.
 *
 * Never repoints a code that already belongs to a different household.
 */
export async function ensureInviteCodePublished(household: Household): Promise<void> {
  if (!household.inviteCode) return
  try {
    const existing = await getDoc(doc(db, 'inviteCodes', household.inviteCode))
    if (existing.exists()) return
    await publishInviteCode(household.id, household.name, household.inviteCode)
  } catch {
    /* Best-effort self-heal; the household screen still renders without it. */
  }
}

export class JoinError extends Error {}

/** Joins the household a code points at, as a member. */
export async function joinHousehold(inviteCode: string, uid: string): Promise<string> {
  const code = inviteCode.trim().toUpperCase()
  if (code.length < 4) throw new JoinError('Enter the full invite code.')

  const lookup = await getDoc(doc(db, 'inviteCodes', code))
  if (!lookup.exists()) throw new JoinError('That invite code does not exist.')

  const householdId = lookup.data().householdId as string
  if (!householdId) throw new JoinError('That invite code is not usable.')

  try {
    // Both keys in one update, as the join branch requires.
    await updateDoc(doc(db, 'households', householdId), {
      memberUids: arrayUnion(uid),
      [`roles.${uid}`]: 'member',
    })
  } catch {
    // The most likely cause by far, and the rules cannot distinguish it for us.
    throw new JoinError(
      'Could not join. The code may have been rotated, or the household may be full.',
    )
  }
  return householdId
}

export async function renameHousehold(household: Household, name: string): Promise<void> {
  await updateDoc(doc(db, 'households', household.id), {
    name: name.trim(),
    // Written alongside so the invariant holds even on a household predating roles.
    roles: normalisedRoles(household),
  })
}

/** Changes a member's role. The owner's own role is not expressible and not offered. */
export async function setMemberRole(
  household: Household,
  uid: string,
  role: RoleWire,
): Promise<void> {
  await updateDoc(doc(db, 'households', household.id), {
    roles: normalisedRoles(household, { setRole: { uid, role } }),
  })
}

/** Ejects a member. Their expenses stay — the rows belong to the household, not the person. */
export async function removeMember(household: Household, uid: string): Promise<void> {
  const memberUids = household.memberUids.filter((m) => m !== uid)
  await updateDoc(doc(db, 'households', household.id), {
    memberUids,
    roles: normalisedRoles(household, { memberUids }),
  })
}

/**
 * Leaves the household. Members and guests only — the owner would orphan it, and since there
 * is no ownership transfer they delete it instead.
 */
export async function leaveHousehold(household: Household, uid: string): Promise<void> {
  const memberUids = household.memberUids.filter((m) => m !== uid)
  const roles = { ...household.roles }
  delete roles[uid]
  // The member branch permits only these two keys to change, and only self-removal.
  await updateDoc(doc(db, 'households', household.id), { memberUids, roles })
}

/** Issues a fresh code and retires the old one. */
export async function rotateInviteCode(household: Household): Promise<string> {
  const inviteCode = generateInviteCode()
  await updateDoc(doc(db, 'households', household.id), {
    inviteCode,
    roles: normalisedRoles(household),
  })
  await publishInviteCode(household.id, household.name, inviteCode)
  if (household.inviteCode) {
    // Retired last: while both exist the old one still works, and if this fails the new code
    // is already live rather than the household being left unjoinable.
    await deleteDoc(doc(db, 'inviteCodes', household.inviteCode)).catch(() => {})
  }
  return inviteCode
}

/**
 * Deletes the household. Owner only.
 *
 * **This does not cascade.** Firestore orphans the subcollections — expenses, categories,
 * budgets and the rest survive with no parent and no way to reach them from the app. Doing
 * this properly needs a Cloud Function; until then the caller is warned explicitly.
 */
export async function deleteHousehold(householdId: string, inviteCode: string): Promise<void> {
  await deleteDoc(doc(db, 'households', householdId))
  if (inviteCode) {
    await deleteDoc(doc(db, 'inviteCodes', inviteCode)).catch(() => {})
  }
}

export interface Member {
  uid: string
  displayName: string
  email: string
  role: RoleWire
  isOwner: boolean
}

/**
 * The member list, with display names read from `users/{uid}`.
 *
 * Reading another member's profile is permitted only when your own `users` document shares a
 * household id with theirs, so a uid that cannot be resolved falls back to a shortened id
 * rather than failing the whole list.
 */
export async function getMembers(household: Household): Promise<Member[]> {
  const owner = household.ownerUid || household.memberUids[0] || ''
  const names = new Map<string, { displayName: string; email: string }>()

  // `documentId() in [...]` is capped at 30 values per query; households are capped at 20
  // members by the rules, so one query always suffices.
  if (household.memberUids.length > 0) {
    try {
      const snapshot = await getDocs(
        query(collection(db, 'users'), where(documentId(), 'in', household.memberUids)),
      )
      snapshot.docs.forEach((d) =>
        names.set(d.id, {
          displayName: (d.data().displayName as string) ?? '',
          email: (d.data().email as string) ?? '',
        }),
      )
    } catch {
      /* Fall through to uid-only rendering. */
    }
  }

  return household.memberUids.map((uid) => {
    const profile = names.get(uid)
    return {
      uid,
      displayName: profile?.displayName || profile?.email || `Member ${uid.slice(0, 6)}`,
      email: profile?.email ?? '',
      role: uid === owner ? 'owner' : ((household.roles[uid] ?? 'member') as RoleWire),
      isOwner: uid === owner,
    }
  })
}
