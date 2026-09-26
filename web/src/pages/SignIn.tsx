import { useState } from 'react'

import {
  authErrorMessage,
  sendPasswordReset,
  signInWithEmail,
  signInWithGoogle,
  signUpWithEmail,
} from '../session/auth'
import { BrandMark } from '../components/Layout'
import { Card, Field, Icon, Notice } from '../components/ui'

export function SignIn() {
  const [mode, setMode] = useState<'signIn' | 'signUp'>('signIn')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  /** Address a reset link was sent to. Set even if no account exists — see sendPasswordReset. */
  const [resetSentTo, setResetSentTo] = useState('')

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    setError('')
    setResetSentTo('')
    try {
      await action()
      // No navigation here: the session provider observes the auth state and swaps the tree.
    } catch (caught) {
      setError(authErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    void run(() =>
      mode === 'signIn'
        ? signInWithEmail(email, password)
        : signUpWithEmail(name, email, password),
    )
  }

  return (
    <div className="centered">
      <Card className="auth-card">
        <div className="row" style={{ gap: 10, marginBottom: 18 }}>
          <BrandMark />
          <div>
            <div style={{ fontWeight: 700 }}>Expense Tracker</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {mode === 'signIn' ? 'Sign in to your household' : 'Create an account'}
            </div>
          </div>
        </div>

        {error !== '' && (
          <div style={{ marginBottom: 14 }}>
            <Notice kind="error">{error}</Notice>
          </div>
        )}

        {resetSentTo !== '' && (
          <div style={{ marginBottom: 14 }}>
            {/* "If an account exists" is load-bearing, not hedging: the send succeeds even for
                an unregistered address so this form cannot be used to discover who has one. */}
            <Notice>
              If an account exists for <strong>{resetSentTo}</strong>, a password reset link is on
              its way. The link expires after an hour.
            </Notice>
          </div>
        )}

        <form onSubmit={submit}>
          {mode === 'signUp' && (
            <Field label="Your name" hint="Shown next to the expenses you add.">
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
                required
              />
            </Field>
          )}

          <Field label="Email">
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </Field>

          <Field label="Password">
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === 'signIn' ? 'current-password' : 'new-password'}
              required
              minLength={6}
            />
          </Field>

          <button className="btn primary" type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? 'Working…' : mode === 'signIn' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        {mode === 'signIn' && (
          <div style={{ textAlign: 'center', marginTop: 10 }}>
            <button
              className="btn ghost"
              type="button"
              style={{ fontSize: 13, color: 'var(--accent-text)' }}
              disabled={busy}
              onClick={() => {
                if (email.trim() === '') {
                  setError('Enter your email address first.')
                  return
                }
                const address = email.trim()
                void run(async () => {
                  await sendPasswordReset(address)
                  setResetSentTo(address)
                })
              }}
            >
              Forgot password?
            </button>
          </div>
        )}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            margin: '16px 0',
            color: 'var(--text-tertiary)',
            fontSize: 12,
          }}
        >
          <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
          or
          <div style={{ flex: 1, height: 1, background: 'var(--border)' }} />
        </div>

        <button
          className="btn"
          type="button"
          style={{ width: '100%', justifyContent: 'center' }}
          disabled={busy}
          onClick={() => void run(signInWithGoogle)}
        >
          <Icon name="login" />
          Continue with Google
        </button>

        <div style={{ marginTop: 18, fontSize: 13, textAlign: 'center' }}>
          {mode === 'signIn' ? "Don't have an account? " : 'Already have an account? '}
          <button
            className="btn ghost"
            type="button"
            style={{ padding: '2px 6px', color: 'var(--accent-text)' }}
            onClick={() => {
              setMode(mode === 'signIn' ? 'signUp' : 'signIn')
              setError('')
            }}
          >
            {mode === 'signIn' ? 'Sign up' : 'Sign in'}
          </button>
        </div>
      </Card>
    </div>
  )
}
