/**
 * Sign-in actions.
 *
 * Google sign-in uses a popup rather than a redirect. The redirect flow needs third-party
 * cookies on the `authDomain`, which Safari and Chrome's privacy modes now block, and it fails
 * by silently returning no user — indistinguishable from a cancellation.
 */
import {
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut as fbSignOut,
  updateProfile,
} from 'firebase/auth'

import { auth, googleProvider } from '../firebase'

/** Firebase error codes turned into something worth showing a user. */
export function authErrorMessage(error: unknown): string {
  const code = (error as { code?: string })?.code ?? ''
  switch (code) {
    case 'auth/invalid-email':
      return 'Enter a valid email address.'
    case 'auth/missing-password':
      return 'Enter your password.'
    case 'auth/weak-password':
      return 'Use a password of at least six characters.'
    case 'auth/email-already-in-use':
      return 'An account with this email already exists. Sign in instead.'
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      // Firebase deliberately conflates these; repeating that avoids telling an attacker
      // which half was wrong.
      return 'Email or password is incorrect.'
    case 'auth/too-many-requests':
      return 'Too many attempts. Wait a minute and try again.'
    case 'auth/missing-email':
      return 'Enter your email address first.'
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return ''
    case 'auth/popup-blocked':
      return 'Your browser blocked the sign-in window. Allow pop-ups for this site and try again.'
    case 'auth/unauthorized-domain':
      return "Google sign-in isn't available on this website yet. Sign in with your email and password instead."
    case 'auth/operation-not-allowed':
      return "This sign-in method isn't available right now. Try another way to sign in."
    default:
      return `Couldn't sign in. ${(error as { message?: string })?.message ?? 'Try again.'}`
  }
}

export async function signInWithEmail(email: string, password: string): Promise<void> {
  await signInWithEmailAndPassword(auth, email.trim(), password)
}

export async function signUpWithEmail(
  name: string,
  email: string,
  password: string,
): Promise<void> {
  const credential = await createUserWithEmailAndPassword(auth, email.trim(), password)
  const displayName = name.trim()
  if (displayName) {
    // Set before the session provider reads it, so the member list never shows a bare uid.
    await updateProfile(credential.user, { displayName })
  }
}

export async function signInWithGoogle(): Promise<void> {
  await signInWithPopup(auth, googleProvider)
}

export async function signOut(): Promise<void> {
  await fbSignOut(auth)
}

/**
 * Sends a password-reset email, resolving even when no account exists for `email`.
 *
 * `auth/user-not-found` is swallowed deliberately. Surfacing it would turn the sign-in form
 * into a way to test whether a given person is registered, and in a family household the
 * addresses are guessable. A malformed address still throws — that is a typo the user can fix,
 * not a disclosure.
 *
 * Newer Firebase projects enable email-enumeration protection server-side and resolve
 * successfully anyway; this keeps the behaviour the same either way, and matches
 * `AuthRepository.sendPasswordReset` in shared/ so all three clients answer identically.
 */
export async function sendPasswordReset(email: string): Promise<void> {
  try {
    await sendPasswordResetEmail(auth, email.trim())
  } catch (error) {
    if ((error as { code?: string })?.code === 'auth/user-not-found') return
    throw error
  }
}
