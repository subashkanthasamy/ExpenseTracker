/**
 * Appearance preference.
 *
 * Three states, matching the mobile apps: explicit light, explicit dark, or follow the system.
 * "System" stores nothing and removes the attribute, so `prefers-color-scheme` decides — the
 * CSS is written so an explicit choice wins in both directions.
 */
export type ThemeChoice = 'system' | 'light' | 'dark'

const KEY = 'expense-tracker.theme'

export function loadTheme(): ThemeChoice {
  try {
    const stored = localStorage.getItem(KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    // Private windows and blocked site data throw on access rather than returning null.
    return 'system'
  }
}

export function applyTheme(choice: ThemeChoice): void {
  const root = document.documentElement
  if (choice === 'system') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', choice)
  try {
    if (choice === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, choice)
  } catch {
    /* Preference is per-browser convenience; failing to persist it is not an error. */
  }
}
