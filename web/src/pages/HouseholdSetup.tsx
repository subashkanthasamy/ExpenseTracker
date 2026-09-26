import { useState } from 'react'
import type { User } from 'firebase/auth'

import { createHousehold, JoinError, joinHousehold } from '../data/household'
import { signOut } from '../session/auth'
import { Card, Field, Icon, Notice } from '../components/ui'

/**
 * Shown when a signed-in user belongs to no household.
 *
 * Reached only from the `noHousehold` session state, never from a failed lookup — the two are
 * separate states precisely so a transient error cannot land a member here and tempt them into
 * creating a second household.
 */
export function HouseholdSetup({ user }: { user: User }) {
  const [mode, setMode] = useState<'create' | 'join'>('create')
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'create') {
        if (name.trim().length === 0) throw new Error('Enter a household name.')
        await createHousehold(name, user.uid)
      } else {
        await joinHousehold(code, user.uid)
      }
      // The households listener in the session provider picks this up and swaps the tree.
    } catch (caught) {
      setError(
        caught instanceof JoinError
          ? caught.message
          : `Something went wrong. ${(caught as Error)?.message ?? 'Try again.'}`,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="centered">
      <Card className="auth-card">
        <h2 style={{ margin: '0 0 4px', fontSize: 18 }}>Set up your household</h2>
        <p style={{ margin: '0 0 18px', fontSize: 13, color: 'var(--text-secondary)' }}>
          Your household is where you and the people you share costs with track expenses together.
        </p>

        <div className="segmented" style={{ marginBottom: 18 }}>
          <button type="button" aria-pressed={mode === 'create'} onClick={() => setMode('create')}>
            Create new
          </button>
          <button type="button" aria-pressed={mode === 'join'} onClick={() => setMode('join')}>
            Join with invite code
          </button>
        </div>

        {error !== '' && (
          <div style={{ marginBottom: 14 }}>
            <Notice kind="error">{error}</Notice>
          </div>
        )}

        <form onSubmit={submit}>
          {mode === 'create' ? (
            <Field
              label="Household name"
              hint="You'll be the owner, and the only person who can delete it."
            >
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="e.g. Home"
                maxLength={100}
                required
              />
            </Field>
          ) : (
            <Field
              label="Invite code"
              hint="6 characters. Find it on the Household screen of the person who invited you."
            >
              <input
                value={code}
                onChange={(event) => setCode(event.target.value.toUpperCase())}
                placeholder="ABC123"
                maxLength={16}
                style={{ letterSpacing: '0.2em', textTransform: 'uppercase' }}
                required
              />
            </Field>
          )}

          <button className="btn primary" type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? (mode === 'create' ? 'Creating…' : 'Joining…') : mode === 'create' ? 'Create household' : 'Join household'}
          </button>
        </form>

        <button
          className="btn ghost"
          type="button"
          style={{ width: '100%', justifyContent: 'center', marginTop: 14 }}
          onClick={() => void signOut()}
        >
          <Icon name="logout" />
          Sign out
        </button>
      </Card>
    </div>
  )
}
