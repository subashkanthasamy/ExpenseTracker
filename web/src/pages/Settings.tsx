import { useState } from 'react'

import { PageHead } from '../components/Layout'
import { Card, CardHeader, Icon, Notice, Segmented, formatDate } from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { signOut } from '../session/auth'
import { useSession } from '../session/SessionProvider'
import { money, paymentMethodLabel, roleDescription, roleLabel } from '../shared'
import { applyTheme, loadTheme, type ThemeChoice } from '../theme'
import type { Expense } from '../types'

/** RFC 4180 quoting: double the quotes and wrap anything containing a delimiter. */
function csvCell(value: string | number): string {
  const text = String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function toCsv(expenses: Expense[]): string {
  const header = ['Date', 'Amount', 'Category', 'Notes', 'Added by', 'Payment method', 'Visibility']
  const rows = expenses.map((expense) => [
    new Date(expense.date).toISOString().slice(0, 10),
    expense.amount,
    expense.categoryName,
    expense.notes,
    expense.addedByName,
    // The label, not the wire value, since a person reads this file.
    expense.paymentMethod === '' ? 'Unspecified' : paymentMethodLabel(expense.paymentMethod),
    expense.scope === 'personal' ? 'Personal' : 'Shared',
  ])
  return [header, ...rows].map((row) => row.map(csvCell).join(',')).join('\n')
}

export function Settings() {
  const session = useSession()
  const { expenses } = useHouseholdData()
  const [theme, setTheme] = useState<ThemeChoice>(loadTheme())

  const chooseTheme = (choice: ThemeChoice) => {
    setTheme(choice)
    applyTheme(choice)
  }

  const download = () => {
    // A plain Blob download: no server round trip, and the file never leaves the browser.
    const blob = new Blob([toCsv(expenses)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `expenses-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  const total = expenses.reduce((sum, expense) => sum + expense.amount, 0)

  return (
    <>
      <PageHead title="Settings" />

      <div className="grid cols-2">
        <Card>
          <CardHeader title="Appearance" sub="Matches your device setting unless you choose one" />
          <Segmented
            options={[
              { value: 'system' as ThemeChoice, label: 'System' },
              { value: 'light' as ThemeChoice, label: 'Light' },
              { value: 'dark' as ThemeChoice, label: 'Dark' },
            ]}
            value={theme}
            onChange={chooseTheme}
          />
        </Card>

        <Card>
          <CardHeader
            title="Export"
            sub={`${expenses.length} ${expenses.length === 1 ? 'expense' : 'expenses'} · ${money(total)}. The file includes only the expenses you can see.`}
          />
          <button className="btn" type="button" onClick={download} disabled={expenses.length === 0}>
            <Icon name="download" />
            Download CSV
          </button>
        </Card>

        <Card>
          <CardHeader title="Account" />
          <div className="t-sm" style={{ lineHeight: 1.7 }}>
            <div>
              <span style={{ color: 'var(--text-secondary)' }}>Signed in as </span>
              {session.displayName}
            </div>
            <div>
              <span style={{ color: 'var(--text-secondary)' }}>Household </span>
              {session.household.name}
            </div>
            <div>
              <span style={{ color: 'var(--text-secondary)' }}>Role </span>
              {roleLabel(session.role)}
              {session.isAdmin && ' (support access)'}
            </div>
            <div>
              <span style={{ color: 'var(--text-secondary)' }}>Created </span>
              {formatDate(session.household.createdAt)}
            </div>
          </div>
          <p style={{ fontSize: 12, color: 'var(--text-tertiary)', marginTop: 10 }}>
            {roleDescription(session.role)}
          </p>
          <button
            className="btn"
            type="button"
            style={{ marginTop: 12 }}
            onClick={() => void signOut()}
          >
            <Icon name="logout" />
            Sign out
          </button>
        </Card>

        <Card>
          <CardHeader title="Not available on the web" />
          <ul
            style={{
              margin: 0,
              paddingLeft: 18,
              fontSize: 13,
              color: 'var(--text-secondary)',
              lineHeight: 1.8,
            }}
          >
            <li>
              <strong>Automatic SMS import.</strong> Browsers can't read your text messages. You
              can paste a bank SMS into the expense form instead.
            </li>
            <li>
              <strong>Adding recurring expenses.</strong> You can set them up here. They're added
              when someone opens the Android or iOS app.
            </li>
            <li>
              <strong>Biometric unlock and reminders.</strong> These need the Android or iOS app.
            </li>
          </ul>
          <div style={{ marginTop: 12 }}>
            <Notice>
              Everything else — expenses, budgets, savings goals, net worth, categories and your
              household — works the same as on Android and iOS, with the same data.
            </Notice>
          </div>
        </Card>
      </div>
    </>
  )
}
