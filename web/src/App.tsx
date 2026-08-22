import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { Layout } from './components/Layout'
import { Card, Notice, Spinner } from './components/ui'
import { HouseholdDataProvider } from './data/HouseholdData'
import { Budgets } from './pages/Budgets'
import { Categories } from './pages/Categories'
import { Dashboard } from './pages/Dashboard'
import { Expenses } from './pages/Expenses'
import { HouseholdPage } from './pages/HouseholdPage'
import { HouseholdSetup } from './pages/HouseholdSetup'
import { Insights } from './pages/Insights'
import { NetWorth } from './pages/NetWorth'
import { Recurring } from './pages/Recurring'
import { Savings } from './pages/Savings'
import { Settings } from './pages/Settings'
import { SignIn } from './pages/SignIn'
import { SessionProvider, useSessionState } from './session/SessionProvider'
import { signOut } from './session/auth'

/**
 * Routes the five session states.
 *
 * `error` is handled separately from `noHousehold` on purpose: a failed household lookup used
 * to be indistinguishable from having no household, which bounced signed-in members into
 * household setup.
 */
function Root() {
  const state = useSessionState()

  switch (state.status) {
    case 'loading':
      return (
        <div className="centered">
          <Spinner label="Loading your household…" />
        </div>
      )

    case 'signedOut':
      return <SignIn />

    case 'noHousehold':
      return <HouseholdSetup user={state.user} />

    case 'error':
      return (
        <div className="centered">
          <Card className="auth-card">
            <Notice kind="error">{state.message}</Notice>
            <div className="row" style={{ gap: 10, marginTop: 16 }}>
              <button className="btn primary" type="button" onClick={() => window.location.reload()}>
                Try again
              </button>
              <button className="btn ghost" type="button" onClick={() => void signOut()}>
                Sign out
              </button>
            </div>
          </Card>
        </div>
      )

    case 'ready':
      return (
        <HouseholdDataProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Dashboard />} />
              <Route path="expenses" element={<Expenses />} />
              <Route path="insights" element={<Insights />} />
              <Route path="budgets" element={<Budgets />} />
              <Route path="goals" element={<Savings />} />
              <Route path="networth" element={<NetWorth />} />
              <Route path="recurring" element={<Recurring />} />
              <Route path="categories" element={<Categories />} />
              <Route path="household" element={<HouseholdPage />} />
              <Route path="settings" element={<Settings />} />
              {/* Unknown paths fall back to the dashboard rather than a blank screen. */}
              <Route path="*" element={<Dashboard />} />
            </Route>
          </Routes>
        </HouseholdDataProvider>
      )
  }
}

export function App() {
  return (
    <BrowserRouter>
      <SessionProvider>
        <Root />
      </SessionProvider>
    </BrowserRouter>
  )
}
