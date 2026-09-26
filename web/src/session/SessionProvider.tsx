/**
 * Who is signed in, which household they are in, and what they may do.
 *
 * Two things here are deliberate rather than incidental:
 *
 * 1. **"No household" and "lookup failed" are different states.** Collapsing them into a
 *    nullable household is the bug that bounced signed-in users to household setup on Android:
 *    a cancelled lookup looked identical to "this user has no household". The discriminated
 *    union below makes the ambiguous case unrepresentable.
 *
 * 2. **The `admin` claim comes from the ID token, never from a document.** `users/{uid}` is
 *    self-writable, so a role field there would be self-grantable. It is a Firebase custom
 *    claim, set server-side with `admin.auth().setCustomUserClaims`, and reaches the client on
 *    the next token refresh.
 */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { onAuthStateChanged, type User } from 'firebase/auth'
import { arrayUnion, collection, doc, onSnapshot, query, setDoc, where } from 'firebase/firestore'

import { millis } from '../data/wire'
import { auth, db } from '../firebase'
import { can as sharedCan, roleOf, type Capability } from '../shared'
import type { Household, RoleWire } from '../types'

/** A signed-in user placed in a household, with their role resolved. */
export interface Session {
  user: User
  uid: string
  displayName: string
  isAdmin: boolean
  household: Household
  role: RoleWire
  /** Convenience over `can(capability, session.role)`. */
  allows: (capability: Capability) => boolean
}

export type SessionState =
  | { status: 'loading' }
  | { status: 'signedOut' }
  /** Signed in, but in no household yet — show create-or-join. */
  | { status: 'noHousehold'; user: User; isAdmin: boolean }
  | { status: 'ready'; session: Session }
  /** The lookup itself failed. Never treat this as "no household". */
  | { status: 'error'; message: string; user: User | null }

const SessionContext = createContext<SessionState>({ status: 'loading' })

function decodeHousehold(id: string, data: Record<string, unknown>): Household {
  return {
    id,
    name: (data.name as string) ?? '',
    memberUids: (data.memberUids as string[]) ?? [],
    ownerUid: (data.ownerUid as string) ?? '',
    roles: (data.roles as Record<string, string>) ?? {},
    inviteCode: (data.inviteCode as string) ?? '',
    createdAt: millis(data.createdAt),
    presetVersion: Number(data.presetVersion ?? 0),
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading' })

  useEffect(() => {
    // Tracks the live household subscription so a sign-out or account switch tears it down.
    let unsubscribeHousehold: (() => void) | null = null
    // Households whose id has already been recorded on the profile, so a snapshot arriving on
    // every household change does not reissue the same write.
    const householdIdsWritten = new Set<string>()

    const stopHousehold = () => {
      unsubscribeHousehold?.()
      unsubscribeHousehold = null
    }

    const unsubscribeAuth = onAuthStateChanged(auth, async (user) => {
      stopHousehold()

      if (!user) {
        setState({ status: 'signedOut' })
        return
      }

      setState({ status: 'loading' })

      let isAdmin = false
      try {
        // Not forced: a refresh on every load would burn a network round trip on every
        // reload. A newly granted claim lands on the next natural token refresh.
        const token = await user.getIdTokenResult()
        isAdmin = token.claims.admin === true
      } catch {
        // A token read failure must not be read as "not an admin" silently downstream, but it
        // is also not fatal: the rules re-check the claim on every request regardless, so the
        // worst case is a control greyed out that the server would have allowed.
        isAdmin = false
      }

      // Keep the profile document in step with the signed-in identity, so the household
      // member list on the mobile apps shows a name rather than a bare uid. Deliberately a
      // merge of only these fields: `users/{uid}` has a strict key allowlist in the rules, and
      // no role may ever live here.
      void setDoc(
        doc(db, 'users', user.uid),
        { email: user.email ?? '', displayName: user.displayName ?? '' },
        { merge: true },
      ).catch(() => {
        /* Non-fatal: the profile is a convenience, not a prerequisite for using the app. */
      })

      // "My households": permitted because the rule tests `uid in resource.data.memberUids`,
      // which Firestore can prove for this query. A rule using get() would reject it.
      const householdsQuery = query(
        collection(db, 'households'),
        where('memberUids', 'array-contains', user.uid),
      )

      unsubscribeHousehold = onSnapshot(
        householdsQuery,
        async (snapshot) => {
          if (snapshot.empty) {
            setState({ status: 'noHousehold', user, isAdmin })
            return
          }
          // A user can belong to several; the mobile apps show one at a time and so does this.
          // First by creation order keeps the choice stable across reloads.
          const households = snapshot.docs
            .map((d) => decodeHousehold(d.id, d.data()))
            .sort((a, b) => a.createdAt - b.createdAt)
          const household = households[0]!
          const role = roleOf(household, user.uid, isAdmin)

          // `users/{uid}.householdIds` is what lets this account read its peers' profiles: the
          // rule permits a profile read only when the two documents share a household id.
          // Without it the household member list renders as bare uids.
          //
          // Awaited, not fire-and-forget, and before the session is published: the household
          // screen reads member profiles as soon as it mounts, so leaving this in flight makes
          // the names resolve or not depending on which write lands first.
          if (!householdIdsWritten.has(household.id)) {
            householdIdsWritten.add(household.id)
            try {
              await setDoc(
                doc(db, 'users', user.uid),
                { householdIds: arrayUnion(household.id), activeHouseholdId: household.id },
                { merge: true },
              )
            } catch {
              /* Non-fatal: the member list degrades to shortened uids. */
            }
          }

          setState({
            status: 'ready',
            session: {
              user,
              uid: user.uid,
              displayName: user.displayName ?? user.email ?? 'You',
              isAdmin,
              household,
              role,
              allows: (capability) => sharedCan(capability, role),
            },
          })
        },
        (error) => {
          // Explicitly an error state, not an empty one.
          setState({
            status: 'error',
            message: error.message
              ? `Couldn't load your household. ${error.message}`
              : "Couldn't load your household. Check your connection and try again.",
            user,
          })
        },
      )
    })

    return () => {
      stopHousehold()
      unsubscribeAuth()
    }
  }, [])

  return <SessionContext.Provider value={state}>{children}</SessionContext.Provider>
}

export const useSessionState = (): SessionState => useContext(SessionContext)

/**
 * The active session.
 *
 * Only callable from inside a rendered app shell, which is mounted only in the `ready` state —
 * so this throwing means a routing mistake, not a user-facing condition.
 */
export function useSession(): Session {
  const state = useSessionState()
  if (state.status !== 'ready') {
    throw new Error('useSession() outside a ready session')
  }
  return state.session
}

/** Whether the current role has `capability`. Greys out controls; does not secure them. */
export function useCan(capability: Capability): boolean {
  return useSession().allows(capability)
}
