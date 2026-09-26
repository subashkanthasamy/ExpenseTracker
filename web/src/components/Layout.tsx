/** The app shell: navigation plus the routed page, in three tiers. */
import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'

import { useSession } from '../session/SessionProvider'
import { roleLabel } from '../shared'
import { Icon, useEscapeToClose } from './ui'

interface NavItem {
  to: string
  label: string
  icon: string
  end?: boolean
}

/**
 * Navigation.
 *
 * The Financial Coach is deliberately absent: `FeatureFlags.FINANCIAL_COACH_ENABLED` is false
 * in the shared module, so it is hidden on Android and iOS too.
 *
 * Every destination here is readable by every role — the rules open reads to the whole
 * household, including guests, because an expense list cannot render a category name
 * otherwise. Write controls are gated inside each page rather than by hiding the page, so a
 * member sees the household's budgets without being offered an Edit button.
 *
 * One flat list at every width. An earlier version split it into four "primary" destinations
 * for a bottom tab bar plus six behind a More drawer; that ranking only existed to fit native
 * app furniture, and without it there is no reason to tell a reader that Categories matters
 * less than Budgets.
 */
const NAV: NavItem[] = [
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
  const { pathname } = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  // Back or forward while the menu is open would otherwise leave it covering the new page.
  // The links close it themselves as well, because navigating to the route you are already on
  // does not change `pathname` and so would not fire this.
  useEffect(() => setMenuOpen(false), [pathname])

  useEscapeToClose(() => setMenuOpen(false))

  const current = NAV.find((item) => (item.end ? pathname === item.to : pathname.startsWith(item.to)))

  return (
    <div className="shell">
      {/* Wide and mid tiers: a vertical sidebar, full-width or collapsed to an icon rail. */}
      <nav className="sidebar" aria-label="Main">
        <div className="brand">
          <BrandMark />
          <span className="brand-name">Expense Tracker</span>
        </div>

        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            // The rail tier hides the label, so this is the only hint left there. Redundant
            // with the visible label at >= 1024px, which is cheaper than a JS width check.
            title={item.label}
            className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
          >
            <Icon name={item.icon} />
            <span className="nav-label">{item.label}</span>
          </NavLink>
        ))}

        <div className="sidebar-footer">
          <Identity />
        </div>
      </nav>

      {/*
        Narrow tier: an ordinary sticky header with a disclosure menu. Always in the DOM and
        hidden by CSS above 719px — `display: none` also removes it from the tab order and the
        accessibility tree, so there is no cost at a wide width and no resize listener.
      */}
      <header className="topbar">
        <button
          className="btn ghost icon"
          type="button"
          aria-expanded={menuOpen}
          aria-controls="nav-panel"
          aria-label={menuOpen ? 'Close navigation menu' : 'Open navigation menu'}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <Icon name={menuOpen ? 'close' : 'menu'} />
        </button>
        <div className="brand">
          <BrandMark />
          {/* The current destination, not the product name: on a narrow screen knowing where
              you are is worth more than being reminded what you opened. */}
          <span>{current?.label ?? 'Expense Tracker'}</span>
        </div>
      </header>

      <nav
        className={`nav-panel${menuOpen ? ' open' : ''}`}
        id="nav-panel"
        aria-label="Main"
      >
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={() => setMenuOpen(false)}
            className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
          >
            <Icon name={item.icon} />
            <span className="nav-label">{item.label}</span>
          </NavLink>
        ))}
        <div className="sidebar-footer">
          <Identity />
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}

/** Extracted so the sidebar footer and the narrow-tier menu footer cannot drift apart. */
function Identity() {
  const session = useSession()
  const initial = session.displayName.trim().charAt(0).toUpperCase() || '?'

  return (
    <div className="identity" title={`${session.displayName} · ${session.household.name}`}>
      {/* The rail is 72px wide, so the initial is all that fits there. */}
      <span className="identity-avatar" aria-hidden="true">
        {initial}
      </span>
      <div style={{ minWidth: 0 }}>
        <div className="identity-name">{session.displayName}</div>
        <div className="identity-meta">
          {session.household.name} · {roleLabel(session.role) || 'No role'}
        </div>
      </div>
    </div>
  )
}

/** Page title block, with optional actions on the right. */
/**
 * The app logo: the white rupee on the brand gradient, the same artwork the Android launcher
 * and the iOS app icon are built from (docs/Expense.png). Decorative — the product name sits
 * beside it everywhere it appears.
 */
export function BrandMark() {
  return <img className="brand-mark" src="/logo-192.png" alt="" width={32} height={32} />
}

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
