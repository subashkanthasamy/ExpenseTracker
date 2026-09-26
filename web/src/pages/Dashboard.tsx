import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { Sparkline, TrendChart, sharePercent } from '../components/charts'
import {
  CategoryIcon,
  ChartSkeleton,
  Empty,
  Icon,
  ListSkeleton,
  Notice,
  Progress,
  Skeleton,
  formatDate,
} from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { categorySlices, dailyCumulative, monthBounds, monthlySeries } from '../data/series'
import { useSession } from '../session/SessionProvider'
import { colorOf, money, moneyShort, sharedOnly } from '../shared'
import type { Category, Expense } from '../types'

/** How far the trend card looks back. Six fits the card without the columns going hairline. */
const TREND_MONTHS = 6

/** "Good morning", by the viewer's clock. */
function greetingFor(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const first = words[0]?.[0] ?? '?'
  const last = words.length > 1 ? (words[words.length - 1]?.[0] ?? '') : ''
  return (first + last).toUpperCase()
}

/**
 * The current time, re-read at each local midnight.
 *
 * Every month figure here is relative to "now", and computing it from a clock read once meant
 * a tab left open across the 1st kept showing last month.
 */
function useToday(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const next = new Date(now)
    next.setHours(24, 0, 1, 0)
    const timer = window.setTimeout(() => setNow(Date.now()), next.getTime() - Date.now())
    return () => window.clearTimeout(timer)
  }, [now])
  return now
}

/**
 * The home screen: the same content as before the redesign — a greeting, four monthly
 * figures, the six-month trend, top categories and recent shared expenses — in the new look.
 *
 * Every figure presented as the household's excludes personal rows. A member cannot see their
 * peers' personal expenses, so including them would give the owner and a member different
 * answers under the same label. Personal spending is shown on its own tile instead.
 */
export function Dashboard() {
  const session = useSession()
  const { expenses, categories, loading, error } = useHouseholdData()
  const nowMillis = useToday()

  const stats = useMemo(() => {
    const { startOfPrevious, startOfThis, startOfNext } = monthBounds(nowMillis)
    const inThisMonth = (e: Expense) => e.date >= startOfThis && e.date < startOfNext

    const shared = sharedOnly(expenses)
    const thisMonth = shared.filter(inThisMonth)
    const lastMonth = shared.filter((e) => e.date >= startOfPrevious && e.date < startOfThis)
    const total = thisMonth.reduce((sum, e) => sum + e.amount, 0)
    const lastTotal = lastMonth.reduce((sum, e) => sum + e.amount, 0)

    return {
      total,
      lastTotal,
      count: thisMonth.length,
      change: lastTotal > 0 ? ((total - lastTotal) / lastTotal) * 100 : null,
      myTotal: expenses.filter((e) => e.addedBy === session.uid && inThisMonth(e)).reduce((s, e) => s + e.amount, 0),
      // Only your own personal rows: that is what the tile's label promises.
      personalTotal: expenses
        .filter((e) => e.scope === 'personal' && e.addedBy === session.uid && inThisMonth(e))
        .reduce((s, e) => s + e.amount, 0),
      // Every personal row this viewer can see, so the headline's gap from the raw month
      // total is stated rather than looking like a miscalculation.
      excludedPersonal: expenses
        .filter((e) => e.scope === 'personal' && inThisMonth(e))
        .reduce((s, e) => s + e.amount, 0),
      pace: dailyCumulative(thisMonth, startOfThis, nowMillis),
      lastMonthPace: dailyCumulative(lastMonth, startOfPrevious, nowMillis),
      topCategories: categorySlices(thisMonth).slice(0, 5),
      recent: shared.slice(0, 6),
      trend: monthlySeries(shared, TREND_MONTHS, nowMillis),
    }
  }, [expenses, session.uid, nowMillis])

  const firstName = session.displayName.split(' ')[0] ?? session.displayName
  const categoryFor = (id: string): Category | undefined => categories.find((c) => c.id === id)

  return (
    <div className="dash">
      <header className="dash-head">
        <span className="dash-avatar" aria-hidden="true">
          {initials(session.displayName)}
        </span>
        <div className="dash-hello">
          <h1>
            {greetingFor(new Date(nowMillis).getHours())}, {firstName}!
          </h1>
          <p>{session.household.name} · this month so far</p>
        </div>
      </header>

      {error != null && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <section className="dash-top" aria-label="This month">
        {/* The headline figure, as the reference's warm card. */}
        <div className="hero-card">
          <div className="hero-top">
            <div>
              <div className="hero-eyebrow">Shared this month</div>
              <div className="hero-name">{session.household.name}</div>
            </div>
            <span className="hero-chip">Shared</span>
          </div>
          <div className="hero-amount">{loading ? '—' : money(stats.total)}</div>
          <div className="hero-foot">
            <div>
              <span className="hero-label">Expenses</span>
              <span>{loading ? '—' : stats.count}</span>
            </div>
            {stats.change != null && !loading && (
              <div>
                <span className="hero-label">vs last month</span>
                <span>
                  {stats.change >= 0 ? '▲' : '▼'} {Math.abs(stats.change).toFixed(0)}%
                </span>
              </div>
            )}
          </div>
          {!loading && (
            <div className="hero-spark" aria-hidden="true">
              <Sparkline values={stats.pace} height={34} accent="var(--hero-ink)" />
            </div>
          )}
        </div>

        <Tile
          tone="cream"
          icon="calendar_month"
          label="Last month"
          value={loading ? null : money(stats.lastTotal)}
          sub="Shared only"
          trend={loading ? undefined : stats.lastMonthPace}
        />
        <Tile
          tone="orange"
          icon="person"
          label="Added by you"
          value={loading ? null : money(stats.myTotal)}
          sub="This month, shared and personal"
        />
        <Tile
          tone="blue"
          icon="lock"
          label="Your personal expenses"
          value={loading ? null : money(stats.personalTotal)}
          sub="Not in shared totals"
        />
      </section>
      {stats.excludedPersonal > 0 && !loading && (
        <p className="dash-footnote">
          Shared totals exclude {money(stats.excludedPersonal)} of personal expenses this month.
        </p>
      )}

      <div className="card dash-trend">
        <div className="card-header">
          <div>
            <h2>Last {TREND_MONTHS} months</h2>
            <p className="sub">Shared spending by month. This month is still in progress.</p>
          </div>
          <Link to="/insights" className="btn ghost">
            Insights
            <Icon name="chevron_right" size={18} />
          </Link>
        </div>
        {loading ? <ChartSkeleton /> : <TrendChart points={stats.trend} />}
      </div>

      <div className="dash-lists">
        <div className="card">
          <div className="card-header">
            <div>
              <h2>Top categories</h2>
              <p className="sub">Shared spending this month</p>
            </div>
            <Link to="/insights" className="btn ghost">
              Insights
              <Icon name="chevron_right" size={18} />
            </Link>
          </div>
          {loading ? (
            <ListSkeleton rows={5} />
          ) : stats.topCategories.length === 0 ? (
            <Empty icon="donut_small" title="No shared expenses this month">
              Add a shared expense to see your top categories.
            </Empty>
          ) : (
            <div className="list">
              {stats.topCategories.map((entry) => {
                const category = categoryFor(entry.categoryId)
                return (
                  <div key={entry.categoryId || entry.name} style={{ padding: '10px 0' }}>
                    <div className="row" style={{ gap: 12, marginBottom: 8 }}>
                      <CategoryIcon
                        name={entry.name}
                        icon={category?.icon}
                        color={category ? colorOf(category) : undefined}
                        size={36}
                      />
                      <span className="t-md t-strong" style={{ flex: 1, minWidth: 0 }}>
                        {entry.name}
                      </span>
                      <span className="t-xs t-secondary">{sharePercent(entry.share)}</span>
                      <strong className="t-md num">{moneyShort(entry.amount)}</strong>
                    </div>
                    <Progress value={entry.share} />
                  </div>
                )
              })}
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <h2>Recent shared expenses</h2>
              <p className="sub">The latest added to the household</p>
            </div>
            <Link to="/expenses" className="btn ghost">
              View all
              <Icon name="chevron_right" size={18} />
            </Link>
          </div>
          {loading ? (
            <ListSkeleton rows={5} />
          ) : stats.recent.length === 0 ? (
            <Empty icon="receipt_long" title="No shared expenses yet">
              Add a shared expense to see it here.
            </Empty>
          ) : (
            <div className="history">
              {stats.recent.map((expense) => {
                const category = categoryFor(expense.categoryId)
                return (
                  <div className="history-row" key={expense.id}>
                    <CategoryIcon
                      name={expense.categoryName}
                      icon={category?.icon}
                      color={category ? colorOf(category) : undefined}
                      size={40}
                    />
                    <div className="history-main">
                      <div className="history-title">{expense.notes.trim() || expense.categoryName}</div>
                      <div className="history-sub">
                        {formatDate(expense.date)}
                        {expense.addedByName.trim() !== '' && <> · {expense.addedByName}</>}
                      </div>
                    </div>
                    <span className="history-amount">{money(expense.amount)}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/** A monthly figure on a pastel icon square, as in the reference's top row. */
function Tile({
  tone,
  icon,
  label,
  value,
  sub,
  trend,
}: {
  tone: 'cream' | 'orange' | 'blue'
  icon: string
  label: string
  value: string | null
  sub?: string
  trend?: number[]
}) {
  return (
    <div className="stat-tile">
      <span className={`stat-tile-icon tone-${tone}`} aria-hidden="true">
        <Icon name={icon} size={24} />
      </span>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className="stat-tile-label">{label}</div>
        {value == null ? <Skeleton width={120} height={28} /> : <div className="stat-tile-value">{value}</div>}
        {sub != null && <div className="stat-tile-sub">{sub}</div>}
        {trend != null && (
          <div className="stat-tile-spark" aria-hidden="true">
            <Sparkline values={trend} height={24} accent="var(--text-tertiary)" />
          </div>
        )}
      </div>
    </div>
  )
}
