/**
 * Time series derived from the expenses already in `useHouseholdData()`.
 *
 * Nothing here reads Firestore. Every figure is a fold over the same array the pages already
 * hold, so adding a chart costs no listener, no query and no composite index — which matters
 * more than it sounds: a member's expense read is already split into two queries by
 * `observeExpenses`, and a third shape would have to be provable under the security rules.
 *
 * Two rules the callers must keep:
 *
 *  - **Run `sharedOnly` first for anything presented as the household's.** A total that
 *    included personal rows would read differently for an owner than for a member under the
 *    same label. See the note on `Permissions.sharedOnly`.
 *  - **Month boundaries are local, not UTC.** `new Date(y, m, 1)` is what Android and iOS
 *    bucket by, and `Dashboard` and `Insights` both used to compute it themselves; the two
 *    copies now live here so a fix lands once.
 */
import type { DateRangeWire, Expense } from '../types'

const sum = (expenses: Expense[]): number => expenses.reduce((total, e) => total + e.amount, 0)

/** Epoch bounds of the month containing `reference`, and of the month before it. */
export function monthBounds(reference: number): {
  startOfPrevious: number
  startOfThis: number
  startOfNext: number
} {
  const date = new Date(reference)
  return {
    startOfPrevious: new Date(date.getFullYear(), date.getMonth() - 1, 1).getTime(),
    startOfThis: new Date(date.getFullYear(), date.getMonth(), 1).getTime(),
    startOfNext: new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime(),
  }
}

/** Epoch bounds for a shared `DateRangeWire`, or null for "all time". */
export function boundsFor(range: DateRangeWire, now = new Date()): { start: number; end: number } | null {
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

export interface MonthPoint {
  /** Short month name; the year is appended in January so a 12-month span stays unambiguous. */
  label: string
  start: number
  end: number
  total: number
  count: number
  /** True for the month in progress — it is partial, so charts de-emphasise it. */
  isCurrent: boolean
}

/**
 * The last `months` calendar months ending with the one in progress, oldest first.
 *
 * Months with nothing in them are present with a total of 0 rather than skipped: a gap in a
 * trend is information, and dropping the point would compress the x-axis and misstate it.
 */
export function monthlySeries(expenses: Expense[], months: number, now = Date.now()): MonthPoint[] {
  const reference = new Date(now)
  const points: MonthPoint[] = []

  for (let offset = months - 1; offset >= 0; offset -= 1) {
    const start = new Date(reference.getFullYear(), reference.getMonth() - offset, 1)
    const end = new Date(reference.getFullYear(), reference.getMonth() - offset + 1, 1)
    const startMillis = start.getTime()
    const endMillis = end.getTime()
    const inMonth = expenses.filter((e) => e.date >= startMillis && e.date < endMillis)

    points.push({
      label:
        start.getMonth() === 0
          ? start.toLocaleDateString(undefined, { month: 'short', year: '2-digit' })
          : start.toLocaleDateString(undefined, { month: 'short' }),
      start: startMillis,
      end: endMillis,
      total: sum(inMonth),
      count: inMonth.length,
      isCurrent: offset === 0,
    })
  }

  return points
}

/**
 * Running total for each day of the month starting at `monthStart`, for a sparkline.
 *
 * The array is as long as the days elapsed so far in the current month (or the whole month
 * for a past one), so the line stops where the data stops rather than trailing along a flat
 * final value — a flat tail reads as "spending stopped", which is not what it means.
 */
export function dailyCumulative(expenses: Expense[], monthStart: number, now = Date.now()): number[] {
  const start = new Date(monthStart)
  const daysInMonth = new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate()
  const isCurrentMonth =
    new Date(now).getFullYear() === start.getFullYear() && new Date(now).getMonth() === start.getMonth()
  const days = isCurrentMonth ? new Date(now).getDate() : daysInMonth

  const perDay = new Array<number>(days).fill(0)
  expenses.forEach((expense) => {
    const date = new Date(expense.date)
    if (date.getFullYear() !== start.getFullYear() || date.getMonth() !== start.getMonth()) return
    const index = date.getDate() - 1
    if (index >= 0 && index < days) perDay[index] = (perDay[index] ?? 0) + expense.amount
  })

  let running = 0
  return perDay.map((amount) => {
    running += amount
    return running
  })
}

export interface CategorySlice {
  categoryId: string
  name: string
  amount: number
  /** 0..1 of the period total. */
  share: number
}

/**
 * Spending per category, largest first.
 *
 * Keyed by `categoryId` and falling back to the name, because a legacy row can carry a
 * category name with no id — two such rows for the same category must still fold together.
 */
export function categorySlices(expenses: Expense[]): CategorySlice[] {
  const total = sum(expenses)
  const byCategory = new Map<string, { categoryId: string; name: string; amount: number }>()

  expenses.forEach((expense) => {
    const key = expense.categoryId || expense.categoryName
    const existing = byCategory.get(key)
    if (existing) existing.amount += expense.amount
    else
      byCategory.set(key, {
        categoryId: expense.categoryId,
        name: expense.categoryName || 'Uncategorised',
        amount: expense.amount,
      })
  })

  return [...byCategory.values()]
    .sort((a, b) => b.amount - a.amount)
    .map((entry) => ({ ...entry, share: total > 0 ? entry.amount / total : 0 }))
}

/**
 * The top `limit` slices, with everything else folded into one "Other".
 *
 * Used to cap the donut. The cap is not cosmetic: `CategoryPresets` holds four near-identical
 * greens (Food, Groceries, Vegetables, Fitness) and four near-identical blues, so past a
 * handful of segments hue stops separating them at all. The legend and the ranked list beside
 * the chart are what actually carry identity — see the comment in `charts.tsx`.
 */
export function cappedSlices(slices: CategorySlice[], limit: number): CategorySlice[] {
  if (slices.length <= limit) return slices
  const head = slices.slice(0, limit - 1)
  const tail = slices.slice(limit - 1)
  const amount = tail.reduce((total, slice) => total + slice.amount, 0)
  return [
    ...head,
    {
      categoryId: '',
      name: `Other (${tail.length})`,
      amount,
      share: tail.reduce((total, slice) => total + slice.share, 0),
    },
  ]
}
