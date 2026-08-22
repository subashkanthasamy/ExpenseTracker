import { useMemo, useState } from 'react'

import { PageHead } from '../components/Layout'
import { Card, CategoryIcon, Empty, Notice, Progress, Segmented, Spinner, Stat } from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { colorOf, money, moneyShort, paymentSplit, personSplit, sharedOnly } from '../shared'
import type { DateRangeWire, Expense } from '../types'

/**
 * Spending breakdowns.
 *
 * Everything here is a share of money actually recorded. There is deliberately no savings
 * rate and no forecast: the app has no income data, so a savings figure would be invented —
 * which is exactly what made the Android screen report "savings" for a month of paid rent.
 */
function boundsFor(range: DateRangeWire): { start: number; end: number } | null {
  const now = new Date()
  switch (range) {
    case 'this_month':
      return {
        start: new Date(now.getFullYear(), now.getMonth(), 1).getTime(),
        end: new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime(),
      }
    case 'last_month':
      return {
        start: new Date(now.getFullYear(), now.getMonth() - 1, 1).getTime(),
        end: new Date(now.getFullYear(), now.getMonth(), 1).getTime(),
      }
    case 'this_year':
      return {
        start: new Date(now.getFullYear(), 0, 1).getTime(),
        end: new Date(now.getFullYear() + 1, 0, 1).getTime(),
      }
    case 'all':
      return null
  }
}

export function Insights() {
  const { expenses, categories, loading, error, canReadAll } = useHouseholdData()
  const [range, setRange] = useState<DateRangeWire>('this_month')

  const data = useMemo(() => {
    const bounds = boundsFor(range)
    const inRange = bounds
      ? expenses.filter((e) => e.date >= bounds.start && e.date < bounds.end)
      : expenses

    // Shared rows only — see the note on Permissions.sharedOnly.
    const shared = sharedOnly(inRange)
    const total = shared.reduce((s, e) => s + e.amount, 0)

    const byCategory = new Map<string, { name: string; categoryId: string; amount: number }>()
    shared.forEach((expense: Expense) => {
      const key = expense.categoryId || expense.categoryName
      const existing = byCategory.get(key)
      if (existing) existing.amount += expense.amount
      else
        byCategory.set(key, {
          name: expense.categoryName || 'Uncategorised',
          categoryId: expense.categoryId,
          amount: expense.amount,
        })
    })

    return {
      total,
      count: shared.length,
      average: shared.length > 0 ? total / shared.length : 0,
      categories: [...byCategory.values()].sort((a, b) => b.amount - a.amount),
      people: personSplit(shared),
      payments: paymentSplit(shared),
    }
  }, [expenses, range])

  return (
    <>
      <PageHead title="Insights" subtitle="Shared spending only — personal expenses are excluded." />

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <div style={{ maxWidth: 460, marginBottom: 20 }}>
        <Segmented
          options={[
            { value: 'this_month' as DateRangeWire, label: 'This month' },
            { value: 'last_month' as DateRangeWire, label: 'Last month' },
            { value: 'this_year' as DateRangeWire, label: 'This year' },
            { value: 'all' as DateRangeWire, label: 'All time' },
          ]}
          value={range}
          onChange={setRange}
        />
      </div>

      {loading ? (
        <Card>
          <Spinner label="Crunching numbers…" />
        </Card>
      ) : (
        <>
          <div className="grid cols-3" style={{ marginBottom: 20 }}>
            <Stat label="Total shared" value={money(data.total)} sub={`${data.count} expenses`} />
            <Stat label="Average expense" value={money(data.average)} />
            <Stat
              label="Top category"
              value={data.categories[0]?.name ?? '—'}
              sub={data.categories[0] != null ? money(data.categories[0].amount) : 'Nothing recorded'}
            />
          </div>

          {!canReadAll && (
            <div style={{ marginBottom: 20 }}>
              <Notice>
                These totals cover shared expenses, which every member sees identically. Personal
                expenses are excluded for everyone, so your figures match the owner's.
              </Notice>
            </div>
          )}

          <div className="grid cols-2">
            <Card>
              <strong style={{ fontSize: 15, display: 'block', marginBottom: 14 }}>By category</strong>
              {data.categories.length === 0 ? (
                <Empty icon="donut_small" title="Nothing in this period" />
              ) : (
                <div className="list">
                  {data.categories.map((entry) => {
                    const category = categories.find((c) => c.id === entry.categoryId)
                    const share = data.total > 0 ? entry.amount / data.total : 0
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
                          <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
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

            <div className="grid" style={{ gap: 'var(--gap)', alignContent: 'start' }}>
              {/* Only meaningful once more than one member has recorded something. A single
                  bar at 100% is noise. */}
              {data.people.length > 1 && (
                <Card>
                  <strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>By person</strong>
                  <p style={{ margin: '0 0 14px', fontSize: 12, color: 'var(--text-secondary)' }}>
                    Share of the household's shared spending — a contribution breakdown, not a
                    leaderboard.
                  </p>
                  <div className="list">
                    {data.people.map((person) => (
                      <div key={person.userId} style={{ padding: '9px 0' }}>
                        <div className="row between" style={{ marginBottom: 6 }}>
                          <span style={{ fontSize: 14, fontWeight: 500 }}>{person.name}</span>
                          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                            {(person.share * 100).toFixed(0)}%
                          </span>
                          <strong style={{ fontSize: 14 }}>{moneyShort(person.amount)}</strong>
                        </div>
                        <Progress value={person.share} />
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              {data.payments.length > 1 && (
                <Card>
                  <strong style={{ fontSize: 15, display: 'block', marginBottom: 14 }}>
                    By payment method
                  </strong>
                  <div className="list">
                    {data.payments.map((slice) => (
                      <div key={slice.id} style={{ padding: '9px 0' }}>
                        <div className="row between" style={{ marginBottom: 6 }}>
                          <span style={{ fontSize: 14, fontWeight: 500 }}>{slice.label}</span>
                          <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                            {(slice.share * 100).toFixed(0)}%
                          </span>
                          <strong style={{ fontSize: 14 }}>{moneyShort(slice.amount)}</strong>
                        </div>
                        <Progress value={slice.share} />
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              {data.people.length <= 1 && data.payments.length <= 1 && (
                <Card>
                  <Empty icon="group" title="Not enough variety yet">
                    Per-person and per-payment-method breakdowns appear once more than one member
                    or method has been used.
                  </Empty>
                </Card>
              )}
            </div>
          </div>
        </>
      )}
    </>
  )
}
