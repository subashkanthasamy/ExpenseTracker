import { useMemo } from 'react'
import { Link } from 'react-router-dom'

import { PageHead } from '../components/Layout'
import { Card, CategoryIcon, Empty, Icon, Notice, Progress, Spinner, Stat, formatDate } from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { useSession } from '../session/SessionProvider'
import { colorOf, money, moneyShort, sharedOnly } from '../shared'
import type { Expense } from '../types'

/** Epoch bounds of the month containing `reference`, and of the month before it. */
function monthBounds(reference: number) {
  const date = new Date(reference)
  const startOfThis = new Date(date.getFullYear(), date.getMonth(), 1).getTime()
  const startOfNext = new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime()
  const startOfPrevious = new Date(date.getFullYear(), date.getMonth() - 1, 1).getTime()
  return { startOfPrevious, startOfThis, startOfNext }
}

const sum = (expenses: Expense[]) => expenses.reduce((total, e) => total + e.amount, 0)

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

    const byCategory = new Map<string, { name: string; amount: number; categoryId: string }>()
    thisMonth.forEach((expense) => {
      const key = expense.categoryId || expense.categoryName
      const existing = byCategory.get(key)
      if (existing) existing.amount += expense.amount
      else
        byCategory.set(key, {
          name: expense.categoryName || 'Uncategorised',
          amount: expense.amount,
          categoryId: expense.categoryId,
        })
    })

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

    return {
      thisMonthTotal: sum(thisMonth),
      lastMonthTotal: sum(lastMonth),
      count: thisMonth.length,
      topCategories: [...byCategory.values()].sort((a, b) => b.amount - a.amount).slice(0, 5),
      myTotal: sum(mine),
      excludedPersonal: sum(personalVisible),
      personalTotal: sum(
        expenses.filter(
          (e) => e.scope === 'personal' && e.addedBy === session.uid && e.date >= startOfThis && e.date < startOfNext,
        ),
      ),
      recent: shared.slice(0, 6),
    }
  }, [expenses, session.uid])

  const change =
    stats.lastMonthTotal > 0
      ? ((stats.thisMonthTotal - stats.lastMonthTotal) / stats.lastMonthTotal) * 100
      : null

  if (loading) {
    return (
      <>
        <PageHead title={`Hello, ${session.displayName.split(' ')[0]}`} />
        <Card>
          <Spinner label="Loading your household…" />
        </Card>
      </>
    )
  }

  return (
    <>
      <PageHead
        title={`Hello, ${session.displayName.split(' ')[0]}`}
        subtitle={`${session.household.name} · this month so far`}
      />

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <div className="grid cols-4" style={{ marginBottom: 20 }}>
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
        />
        <Stat label="Last month" value={money(stats.lastMonthTotal)} sub="Shared only" />
        <Stat label="Added by you" value={money(stats.myTotal)} sub="This month, all scopes" />
        <Stat
          label="Your personal"
          value={money(stats.personalTotal)}
          sub="Not in shared totals"
          accent="var(--accent-orange)"
        />
      </div>

      <div className="grid cols-2">
        <Card>
          <div className="row between" style={{ marginBottom: 14 }}>
            <strong style={{ fontSize: 15 }}>Top categories</strong>
            <Link to="/insights" className="btn ghost" style={{ fontSize: 13 }}>
              Insights
              <Icon name="chevron_right" size={17} />
            </Link>
          </div>

          {stats.topCategories.length === 0 ? (
            <Empty icon="donut_small" title="Nothing this month yet">
              Shared expenses added this month will break down here.
            </Empty>
          ) : (
            <div className="list">
              {stats.topCategories.map((entry) => {
                const category = categories.find((c) => c.id === entry.categoryId)
                const share = stats.thisMonthTotal > 0 ? entry.amount / stats.thisMonthTotal : 0
                return (
                  <div key={entry.categoryId || entry.name} style={{ padding: '9px 0' }}>
                    <div className="row" style={{ gap: 10, marginBottom: 6 }}>
                      <CategoryIcon
                        name={entry.name}
                        icon={category?.icon}
                        color={category ? colorOf(category) : undefined}
                        size={30}
                      />
                      <span style={{ fontSize: 14, fontWeight: 500, flex: 1 }}>{entry.name}</span>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                        {(share * 100).toFixed(0)}%
                      </span>
                      <strong style={{ fontSize: 14, fontVariantNumeric: 'tabular-nums' }}>
                        {moneyShort(entry.amount)}
                      </strong>
                    </div>
                    <Progress value={share} />
                  </div>
                )
              })}
            </div>
          )}
        </Card>

        <Card>
          <div className="row between" style={{ marginBottom: 14 }}>
            <strong style={{ fontSize: 15 }}>Recent shared expenses</strong>
            <Link to="/expenses" className="btn ghost" style={{ fontSize: 13 }}>
              All
              <Icon name="chevron_right" size={17} />
            </Link>
          </div>

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
