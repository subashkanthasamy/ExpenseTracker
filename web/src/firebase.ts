/**
 * Firebase initialisation.
 *
 * The web app is a **separate app registration** in the same Firebase project as Android and
 * iOS — it is not in `google-services.json` or `GoogleService-Info.plist`. Create it under
 * Project settings > Your apps > Add app > Web, then copy the values into `web/.env`
 * (see `.env.example`).
 *
 * These values are public by design. They identify the project; they authorise nothing.
 * `firestore.rules` is what stands between a caller and the data, on web exactly as on mobile.
 */
import { initializeApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider } from 'firebase/auth'
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from 'firebase/firestore'

const required = [
  'VITE_FIREBASE_API_KEY',
  'VITE_FIREBASE_AUTH_DOMAIN',
  'VITE_FIREBASE_PROJECT_ID',
  'VITE_FIREBASE_APP_ID',
] as const

const missing = required.filter((key) => !import.meta.env[key])
if (missing.length > 0) {
  // Failing loudly here beats a blank screen and an opaque `auth/invalid-api-key` later.
  throw new Error(
    `Firebase config missing: ${missing.join(', ')}. ` +
      'Copy web/.env.example to web/.env and fill in the Web app values from the Firebase console.',
  )
}

const app = initializeApp({
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
})

export const auth = getAuth(app)

export const googleProvider = new GoogleAuthProvider()

/**
 * Firestore with the IndexedDB cache enabled.
 *
 * This is the web's equivalent of Android's Room cache, and it comes for free: reads are
 * served locally when offline and writes queue until reconnect. `persistentMultipleTabManager`
 * matters because a browser user genuinely does open two tabs — without it the second tab
 * fails to acquire the cache lease.
 */
export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
})
