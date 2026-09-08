import { useMemo } from 'react'

import { DonutChart, Legend, type DonutSlice } from '../components/charts'
import { PageHead } from '../components/Layout'
import {
  Card,
  CardHeader,
  CategoryIcon,
  Empty,
  ListSkeleton,
  Notice,
  Progress,
  Segmented,
  Stat,
  StatSkeleton,
} from '../components/ui'
import { useHouseholdData } from '../data/HouseholdData'
import { boundsFor, cappedSlices, categorySlices } from '../data/series'
import { colorOf, money, moneyShort, paymentSplit, personSplit, sharedOnly } from '../shared'
import type { DateRangeWire } from '../types'
import { useUrlEnum } from '../urlState'

/** Kept in the URL so a breakdown can be linked and survives a reload. */
const RANGES: readonly DateRangeWire[] = ['this_month', 'last_month', 'this_year', 'all']

/**
 * Spending breakdowns.
 *
 * Everything here is a share of money actually recorded. There is deliberately no savings
 * rate and no forecast: the app has no income data, so a savings figure would be invented —
 * which is exactly what made the Android screen report "savings" for a month of paid rent.
 *
 * There is also deliberately no trend chart on this screen, even though `monthlySeries` exists
 * and the Dashboard uses it. The range control at the top scopes everything below it, and a
 * six-month trend sitting under a filter reading "This month" would contradict it. The trend
 * belongs on the Dashboard, which has no range control.
 */

/**
 * Segments in the donut, including the folded "Other".
 *
 * Six is the ceiling for part-to-whole at a glance, and in this app it is a hard cap for a
 * second reason — see the comment on `DonutChart`: the shared category palette has four
 * near-identical greens and four near-identical blues, so past a handful of arcs hue stops
 * separating them at all.
 */
const DONUT_SEGMENTS = 6

export function Insights() {
  const { expenses, categories, loading, error, canReadAll } = useHouseholdData()
  const [range, setRange] = useUrlEnum<DateRangeWire>('range', RANGES, 'this_month')

  const data = useMemo(() => {
    const bounds = boundsFor(range)
    const inRange = bounds
      ? expenses.filter((e) => e.date >= bounds.start && e.date < bounds.end)
      : expenses

    // Shared rows only — see the note on Permissions.sharedOnly.
    const shared = sharedOnly(inRange)
    const total = shared.reduce((sum, e) => sum + e.amount, 0)
    const slices = categorySlices(shared)

    return {
      total,
      count: shared.length,
      average: shared.length > 0 ? total / shared.length : 0,
      categories: slices,
      donut: cappedSlices(slices, DONUT_SEGMENTS),
      people: personSplit(shared),
      payments: paymentSplit(shared),
    }
  }, [expenses, range])

  const donutSlices: DonutSlice[] = data.donut.map((slice) => {
    const category = categories.find((c) => c.id === slice.categoryId)
    return {
      id: slice.categoryId || slice.name,
      label: slice.name,
      value: slice.amount,
      // The folded tail is not a category, so it takes the de-emphasis grey rather than
      // borrowing a hue that would read as one.
      color: slice.categoryId === '' ? 'var(--text-tertiary)' : category ? colorOf(category) : 'var(--accent)',
    }
  })

  return (
    <>
      <PageHead title="Insights" subtitle="Shared spending only — personal expenses are excluded." />

      {error != null && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      {/* One filter row, above everything it scopes, so every figure below agrees. */}
      <div className="toolbar" style={{ maxWidth: 460 }}>
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
        <>
          <StatSkeleton count={3} />
          <div className="grid cols-2">
            <Card>
              <CardHeader title="By category" />
              <ListSkeleton rows={6} />
            </Card>
            <Card>
              <CardHeader title="By person" />
              <ListSkeleton rows={3} />
            </Card>
          </div>
        </>
      ) : (
        <>
          <div className="grid cols-3" style={{ marginBottom: 'var(--space-5)' }}>
            <Stat label="Total shared" value={money(data.total)} sub={`${data.count} expenses`} />
            <Stat label="Average expense" value={money(data.average)} />
            <Stat
              label="Top category"
              value={data.categories[0]?.name ?? '—'}
              sub={data.categories[0] != null ? money(data.categories[0].amount) : 'Nothing recorded'}
            />
          </div>

          {!canReadAll && (
            <div style={{ marginBottom: 'var(--space-5)' }}>
              <Notice>
                These totals cover shared expenses, which every member sees identically. Personal
                expenses are excluded for everyone, so your figures match the owner's.
              </Notice>
            </div>
          )}

          <div className="grid cols-2">
            <Card>
              <CardHeader
                title="By category"
                sub={
                  data.categories.length > DONUT_SEGMENTS
                    ? `Top ${DONUT_SEGMENTS - 1} shown separately, the remaining ${
                        data.categories.length - (DONUT_SEGMENTS - 1)
                      } grouped`
                    : undefined
                }
              />
              {data.categories.length === 0 ? (
                <Empty icon="donut_small" title="Nothing in this period" />
              ) : (
                <>
                  {/*
                    The donut answers "roughly how is it split"; the ranked list underneath is
                    its table twin and answers "how much exactly", so no value here is
                    reachable only by hovering. The legend is not optional — it, not the fill,
                    is what tells two categories apart.
                  */}
                  <div
                    className="row wrap"
                    style={{ gap: 'var(--space-5)', alignItems: 'center', marginBottom: 'var(--space-4)' }}
                  >
                    <DonutChart slices={donutSlices} total={data.total} centerLabel="shared" />
                    <div style={{ flex: 1, minWidth: 150 }}>
                      <Legend slices={donutSlices} />
                    </div>
                  </div>

                  <div className="list">
                    {data.categories.map((entry) => {
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
                            <span className="t-xs t-secondary">{(entry.share * 100).toFixed(0)}%</span>
                            <strong className="t-md num">{moneyShort(entry.amount)}</strong>
                          </div>
                          <Progress value={entry.share} />
                        </div>
                      )
                    })}
                  </div>
                </>
              )}
            </Card>

            <div className="grid" style={{ gap: 'var(--gap)', alignContent: 'start' }}>
              {/* Only meaningful once more than one member has recorded something. A single
                  bar at 100% is noise. */}
              {data.people.length > 1 && (
                <Card>
                  <CardHeader
                    title="By person"
                    sub="Share of the household's shared spending — a contribution breakdown, not a leaderboard."
                  />
                  <div className="list">
                    {data.people.map((person) => (
                      <div key={person.userId} style={{ padding: '9px 0' }}>
                        <div className="row between" style={{ marginBottom: 6 }}>
                          <span className="t-md t-strong">{person.name}</span>
                          <span className="t-sm t-secondary">{(person.share * 100).toFixed(0)}%</span>
                          <strong className="t-md num">{moneyShort(person.amount)}</strong>
                        </div>
                        <Progress value={person.share} />
                      </div>
                    ))}
                  </div>
                </Card>
              )}

              {data.payments.length > 1 && (
                <Card>
                  <CardHeader title="By payment method" />
                  <div className="list">
                    {data.payments.map((slice) => (
                      <div key={slice.id} style={{ padding: '9px 0' }}>
                        <div className="row between" style={{ marginBottom: 6 }}>
                          <span className="t-md t-strong">{slice.label}</span>
                          <span className="t-sm t-secondary">{(slice.share * 100).toFixed(0)}%</span>
                          <strong className="t-md num">{moneyShort(slice.amount)}</strong>
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
