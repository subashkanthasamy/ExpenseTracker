/** The app shell: sidebar navigation plus the routed page. */
import { NavLink, Outlet } from 'react-router-dom'

import { useSession } from '../session/SessionProvider'
import { roleLabel } from '../shared'
import { Icon } from './ui'

/**
 * Navigation.
 *
 * The Financial Coach is deliberately absent: `FeatureFlags.FINANCIAL_COACH_ENABLED` is false
 * in the shared module, so it is hidden on Android and iOS too.
 *
 * Every destination here is readable by every role — the rules open reads to the whole
 * household, including guests, because an expense list cannot render a category name otherwise.
 * Write controls are gated inside each page rather than by hiding the page, so a member sees
 * the household's budgets without being offered an Edit button.
 */
const NAV = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', end: true },
  { to: '/expenses', label: 'Expenses', icon: 'receipt_long' },
  { to: '/insights', label: 'Insights', icon: 'insights' },
  { to: '/budgets', label: 'Budgets', icon: 'savings' },
  { to: '/goals', label: 'Savings goals', icon: 'flag' },
  { to: '/networth', label: 'Net worth', icon: 'account_balance' },
  { to: '/recurring', label: 'Recurring', icon: 'autorenew' },
  { to: '/categories', label: 'Categories', icon: 'category' },
  { to: '/household', label: 'Household', icon: 'group' },
  { to: '/settings', label: 'Settings', icon: 'settings' },
]

export function Layout() {
  const session = useSession()

  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">
          <span className="brand-mark">
            <Icon name="wallet" size={18} />
          </span>
          <span>Expense Tracker</span>
        </div>

        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
          >
            <Icon name={item.icon} />
            <span>{item.label}</span>
          </NavLink>
        ))}

        <div className="sidebar-footer">
          <div style={{ padding: '4px 12px' }}>
            <div style={{ fontSize: 13, fontWeight: 600 }}>{session.displayName}</div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
              {session.household.name} · {roleLabel(session.role) || 'No role'}
            </div>
          </div>
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}

/** Page title block, with optional actions on the right. */
export function PageHead({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: React.ReactNode
  children?: React.ReactNode
}) {
  return (
    <header className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle != null && <p>{subtitle}</p>}
      </div>
      {children != null && <div className="row wrap">{children}</div>}
    </header>
  )
}
