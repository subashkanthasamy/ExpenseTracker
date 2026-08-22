/**
 * Sign-in actions.
 *
 * Google sign-in uses a popup rather than a redirect. The redirect flow needs third-party
 * cookies on the `authDomain`, which Safari and Chrome's privacy modes now block, and it fails
 * by silently returning no user — indistinguishable from a cancellation.
 */
import {
  createUserWithEmailAndPassword,
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
      return 'That email address is not valid.'
    case 'auth/missing-password':
      return 'Enter your password.'
    case 'auth/weak-password':
      return 'Use a password of at least six characters.'
    case 'auth/email-already-in-use':
      return 'That email already has an account. Try signing in instead.'
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      // Firebase deliberately conflates these; repeating that avoids telling an attacker
      // which half was wrong.
      return 'Email or password is incorrect.'
    case 'auth/too-many-requests':
      return 'Too many attempts. Wait a minute and try again.'
    case 'auth/popup-closed-by-user':
    case 'auth/cancelled-popup-request':
      return ''
    case 'auth/popup-blocked':
      return 'Your browser blocked the sign-in window. Allow popups for this site and retry.'
    case 'auth/unauthorized-domain':
      return 'This domain is not authorised in Firebase Authentication > Settings > Authorized domains.'
    case 'auth/operation-not-allowed':
      return 'That sign-in method is disabled in the Firebase console.'
    default:
      return (error as { message?: string })?.message ?? 'Something went wrong. Try again.'
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
