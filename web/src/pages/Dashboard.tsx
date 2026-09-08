import { useMemo } from 'react'
import { Link } from 'react-router-dom'

import { Sparkline, TrendChart } from '../components/charts'
import { PageHead } from '../components/Layout'
import {
  Card,
  CardHeader,
  CategoryIcon,
  ChartSkeleton,
  Empty,
  Icon,
  ListSkeleton,
  Notice,
  Progress,
  Stat,
  StatSkeleton,
  formatDate,
} from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { categorySlices, dailyCumulative, monthBounds, monthlySeries } from '../data/series'
import { useSession } from '../session/SessionProvider'
import { colorOf, money, moneyShort, sharedOnly } from '../shared'

/** How far the trend card looks back. Six fits the card without the columns going hairline. */
const TREND_MONTHS = 6

export function Dashboard() {
  const session = useSession()
  const { expenses, categories, loading, error } = useHouseholdData()

  const stats = useMemo(() => {
    const { startOfPrevious, startOfThis, startOfNext } = monthBounds(Date.now())

    // Every figure presented as the household's excludes personal rows. A member cannot see
    // their peers' personal expenses, so including them would give the owner and a member
    // different answers under the same label.
    const shared = sharedOnly(expenses)
    const thisMonth = shared.filter((e) => e.date >= startOfThis && e.date < startOfNext)
    const lastMonth = shared.filter((e) => e.date >= startOfPrevious && e.date < startOfThis)

    const mine = expenses.filter(
      (e) => e.addedBy === session.uid && e.date >= startOfThis && e.date < startOfNext,
    )

    // Everything personal this viewer can see. For a member that is only their own rows; for
    // the owner and admins it is everyone's. Surfacing it is what stops the headline looking
    // wrong: the shared figure is deliberately smaller than the raw month total, and without
    // the difference stated the number reads as a miscalculation.
    const personalVisible = expenses.filter(
      (e) => e.scope === 'personal' && e.date >= startOfThis && e.date < startOfNext,
    )

    const total = thisMonth.reduce((sum, e) => sum + e.amount, 0)

    return {
      thisMonthTotal: total,
      lastMonthTotal: lastMonth.reduce((sum, e) => sum + e.amount, 0),
      count: thisMonth.length,
      // Top five by amount, each row directly labelled with its name and value — which is
      // what makes the category tint decoration rather than the identity channel. See the
      // note on DonutChart for why that distinction is load-bearing in this app.
      topCategories: categorySlices(thisMonth).slice(0, 5),
      myTotal: mine.reduce((sum, e) => sum + e.amount, 0),
      excludedPersonal: personalVisible.reduce((sum, e) => sum + e.amount, 0),
      personalTotal: expenses
        .filter(
          (e) =>
            e.scope === 'personal' &&
            e.addedBy === session.uid &&
            e.date >= startOfThis &&
            e.date < startOfNext,
        )
        .reduce((sum, e) => sum + e.amount, 0),
      recent: shared.slice(0, 6),
      // Running total across the days elapsed so far, so the tile answers "on track or not"
      // without a second number.
      pace: dailyCumulative(thisMonth, startOfThis),
      lastMonthPace: dailyCumulative(lastMonth, startOfPrevious),
      trend: monthlySeries(shared, TREND_MONTHS),
    }
  }, [expenses, session.uid])

  const change =
    stats.lastMonthTotal > 0
      ? ((stats.thisMonthTotal - stats.lastMonthTotal) / stats.lastMonthTotal) * 100
      : null

  const greeting = `Hello, ${session.displayName.split(' ')[0]}`

  if (loading) {
    return (
      <>
        <PageHead title={greeting} subtitle={`${session.household.name} · this month so far`} />
        <StatSkeleton />
        <Card style={{ marginBottom: 'var(--space-5)' }}>
          <CardHeader title={`Last ${TREND_MONTHS} months`} />
          <ChartSkeleton />
        </Card>
        <div className="grid cols-2">
          <Card>
            <CardHeader title="Top categories" />
            <ListSkeleton rows={5} />
          </Card>
          <Card>
            <CardHeader title="Recent shared expenses" />
            <ListSkeleton rows={5} />
          </Card>
        </div>
      </>
    )
  }

  return (
    <>
      <PageHead title={greeting} subtitle={`${session.household.name} · this month so far`} />

      {error != null && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <div className="grid cols-4" style={{ marginBottom: 'var(--space-5)' }}>
        <Stat
          label="Shared this month"
          value={money(stats.thisMonthTotal)}
          sub={
            <>
              {change == null
                ? `${stats.count} expenses`
                : `${change >= 0 ? '▲' : '▼'} ${Math.abs(change).toFixed(0)}% vs last month`}
              {stats.excludedPersonal > 0 && (
                <>
                  <br />
                  Excludes {money(stats.excludedPersonal)} personal
                </>
              )}
            </>
          }
          trend={<Sparkline values={stats.pace} />}
        />
        <Stat
          label="Last month"
          value={money(stats.lastMonthTotal)}
          sub="Shared only"
          trend={<Sparkline values={stats.lastMonthPace} accent="var(--text-tertiary)" />}
        />
        <Stat label="Added by you" value={money(stats.myTotal)} sub="This month, all scopes" />
        <Stat
          label="Your personal"
          value={money(stats.personalTotal)}
          sub="Not in shared totals"
          accent="var(--accent-orange-text)"
        />
      </div>

      {/*
        The one thing the web client showed nowhere before: change over time. One series, so
        the colour job is sequential and there is no legend — the heading already names what
        is plotted.
      */}
      <Card style={{ marginBottom: 'var(--space-5)' }}>
        <CardHeader
          title={`Last ${TREND_MONTHS} months`}
          sub="Shared spending per calendar month. The current month is partial."
          action={
            <Link to="/insights" className="btn ghost t-sm">
              Insights
              <Icon name="chevron_right" size={17} />
            </Link>
          }
        />
        <TrendChart points={stats.trend} />
      </Card>

      <div className="grid cols-2">
        <Card>
          <CardHeader
            title="Top categories"
            action={
              <Link to="/insights" className="btn ghost t-sm">
                Insights
                <Icon name="chevron_right" size={17} />
              </Link>
            }
          />

          {stats.topCategories.length === 0 ? (
            <Empty icon="donut_small" title="Nothing this month yet">
              Shared expenses added this month will break down here.
            </Empty>
          ) : (
            <div className="list">
              {stats.topCategories.map((entry) => {
                const category = categories.find((c) => c.id === entry.categoryId)
                return (
                  <div key={entry.categoryId || entry.name} style={{ padding: '9px 0' }}>
                    <div className="row" style={{ gap: 10, marginBottom: 6 }}>
                      <CategoryIcon
                        name={entry.name}
                        icon={category?.icon}
                        color={category ? colorOf(category) : undefined}
                        size={30}
                      />
                      <span className="t-md t-strong" style={{ flex: 1, minWidth: 0 }}>
                        {entry.name}
                      </span>
                      <span className="t-sm t-secondary">{(entry.share * 100).toFixed(0)}%</span>
                      <strong className="t-md num">{moneyShort(entry.amount)}</strong>
                    </div>
                    <Progress value={entry.share} />
                  </div>
                )
              })}
            </div>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Recent shared expenses"
            action={
              <Link to="/expenses" className="btn ghost t-sm">
                All
                <Icon name="chevron_right" size={17} />
              </Link>
            }
          />

          {stats.recent.length === 0 ? (
            <Empty icon="receipt_long" title="No expenses yet">
              Anything added on Android or iOS appears here too.
            </Empty>
          ) : (
            <div className="list">
              {stats.recent.map((expense) => {
                const category = categories.find((c) => c.id === expense.categoryId)
                return (
                  <div className="list-row" key={expense.id}>
                    <CategoryIcon
                      name={expense.categoryName}
                      icon={category?.icon}
                      color={category ? colorOf(category) : undefined}
                      size={32}
                    />
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div className="title">
                        {expense.notes.trim() !== '' ? expense.notes : expense.categoryName}
                      </div>
                      <div className="meta">
                        <span>{formatDate(expense.date)}</span>
                        {expense.addedByName.trim() !== '' && (
                          <>
                            <span>·</span>
                            <span>{expense.addedByName}</span>
                          </>
                        )}
                      </div>
                    </div>
                    <div className="amount">{money(expense.amount)}</div>
                  </div>
                )
              })}
            </div>
          )}
        </Card>
      </div>
    </>
  )
}
