/**
 * Screen state that belongs in the address bar.
 *
 * A filtered list, a sort order and an open form are all *where you are* in a web app, not
 * private component state. Keeping them in the query string is what makes the browser work
 * the way a browser should: the URL is linkable, a reload restores what you were looking at,
 * and Back closes a dialog instead of leaving the app.
 *
 * Two rules the hooks below encode, because getting either wrong is worse than not doing this
 * at all:
 *
 * 1. **Filters replace, navigation pushes.** Typing into a search box must not add a history
 *    entry per keystroke, or Back becomes a broken undo of your own typing. Opening a form
 *    *must* push one, because that is the entry Back is expected to pop.
 * 2. **A default is an absent parameter.** Writing `?range=all&sort=date&dir=desc` for the
 *    state you get by visiting the page makes every URL noisy and makes "is anything
 *    filtered?" a comparison rather than a lookup.
 *
 * Transient confirmations stay out of the URL on purpose — see `useConfirmAction`. A
 * "delete this?" dialog is not a place, and a reload that reopened it would be alarming.
 */
import { useCallback, useMemo, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { EMPTY_CRITERIA, type DateRangeWire, type FilterCriteria, type PaymentWire } from './types'

const RANGES: DateRangeWire[] = ['all', 'this_month', 'last_month', 'this_year']
const PAYMENTS: PaymentWire[] = ['', 'cash', 'upi', 'credit_card']

export type SortKey = 'date' | 'amount' | 'category' | 'person'
export type SortDir = 'asc' | 'desc'

const SORT_KEYS: SortKey[] = ['date', 'amount', 'category', 'person']

export interface Sort {
  key: SortKey
  dir: SortDir
}

/** Newest first, matching the order the Firestore listener already returns. */
export const DEFAULT_SORT: Sort = { key: 'date', dir: 'desc' }

/**
 * The expense list's filters, sort and open dialog, mirrored to the query string.
 *
 * `?q=milk&category=preset_x_groceries&range=this_month&sort=amount&dir=desc`
 */
export function useExpenseParams() {
  const [params, setParams] = useSearchParams()

  const criteria = useMemo<FilterCriteria>(() => {
    const range = params.get('range')
    const payment = params.get('payment')
    return {
      searchQuery: params.get('q') ?? '',
      categoryFilter: params.get('category'),
      personFilter: params.get('person'),
      // An unrecognised value is dropped rather than trusted: these arrive from a URL a
      // person can edit, and `filterExpenses` would silently match nothing on a bad one.
      paymentMethodFilter:
        payment != null && (PAYMENTS as string[]).includes(payment) ? (payment as PaymentWire) : null,
      dateRange: range != null && (RANGES as string[]).includes(range) ? (range as DateRangeWire) : 'all',
    }
  }, [params])

  const sort = useMemo<Sort>(() => {
    const key = params.get('sort')
    const dir = params.get('dir')
    return {
      key: key != null && (SORT_KEYS as string[]).includes(key) ? (key as SortKey) : DEFAULT_SORT.key,
      dir: dir === 'asc' || dir === 'desc' ? dir : DEFAULT_SORT.dir,
    }
  }, [params])

  /** A filter or sort change: replaces, so Back does not walk through your keystrokes. */
  const write = useCallback(
    (mutate: (next: URLSearchParams) => void) => {
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous)
          mutate(next)
          return next
        },
        { replace: true },
      )
    },
    [setParams],
  )

  const setCriteria = useCallback(
    (patch: Partial<FilterCriteria>) => {
      write((next) => {
        const merged = { ...criteria, ...patch }
        setOrDelete(next, 'q', merged.searchQuery, '')
        setOrDelete(next, 'category', merged.categoryFilter, null)
        setOrDelete(next, 'person', merged.personFilter, null)
        setOrDelete(next, 'payment', merged.paymentMethodFilter, null)
        setOrDelete(next, 'range', merged.dateRange, 'all')
      })
    },
    [criteria, write],
  )

  const clearCriteria = useCallback(() => {
    write((next) => {
      for (const key of ['q', 'category', 'person', 'payment', 'range']) next.delete(key)
    })
  }, [write])

  /**
   * Clicking a column header. Re-clicking the active column flips direction; a new column
   * starts descending for the two numeric-ish keys and ascending for the two textual ones,
   * which is the direction a reader wants first in each case.
   */
  const toggleSort = useCallback(
    (key: SortKey) => {
      write((next) => {
        const dir: SortDir =
          sort.key === key
            ? sort.dir === 'asc'
              ? 'desc'
              : 'asc'
            : key === 'date' || key === 'amount'
              ? 'desc'
              : 'asc'
        setOrDelete(next, 'sort', key, DEFAULT_SORT.key)
        setOrDelete(next, 'dir', dir, DEFAULT_SORT.dir)
      })
    },
    [sort, write],
  )

  const active =
    criteria.searchQuery.trim() !== '' ||
    criteria.categoryFilter != null ||
    criteria.personFilter != null ||
    criteria.paymentMethodFilter != null ||
    criteria.dateRange !== 'all'

  return { criteria, setCriteria, clearCriteria, active, sort, toggleSort }
}

/**
 * A dialog whose open state is a place: `?new=1`, `?edit=<id>`, `?filters=1`.
 *
 * Opening pushes a history entry, so Back closes the dialog. Closing has to undo that entry,
 * and *how* depends on where the dialog came from:
 *
 *  - **Opened here** — pop the entry with `navigate(-1)`. Replacing it instead would leave a
 *    duplicate of the current URL on the stack, and the next Back press would look broken:
 *    the URL changes to the same thing and nothing moves.
 *  - **Arrived already open**, i.e. someone followed a link straight to `?edit=<id>` — there
 *    is no entry of ours to pop, and `navigate(-1)` would take them off the site. Replace.
 *
 * The ref is what distinguishes the two, and it is a ref rather than state because flipping it
 * must not re-render the dialog mid-dismiss.
 */
export function useUrlDialog(key: string) {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const pushedByUs = useRef(false)
  // The query string as it was when we opened, minus our own key. A dialog like Filters edits
  // the URL while it is open (each choice replaces the entry we pushed), and popping that
  // entry would throw every one of those choices away — which is exactly what happened: pick
  // a filter, press Done, and the list came back unfiltered.
  const openedFrom = useRef('')
  const value = params.get(key)

  const open = useCallback(
    (next: string = '1') => {
      pushedByUs.current = true
      setParams((previous) => {
        openedFrom.current = previous.toString()
        const merged = new URLSearchParams(previous)
        merged.set(key, next)
        return merged
      })
    },
    [key, setParams],
  )

  const close = useCallback(() => {
    const now = new URLSearchParams(params)
    now.delete(key)
    // Pop only when the dialog changed nothing else, so Back still undoes a plain open/close.
    // Otherwise drop just our key and keep what was chosen inside the dialog.
    if (pushedByUs.current && now.toString() === openedFrom.current) {
      pushedByUs.current = false
      navigate(-1)
      return
    }
    pushedByUs.current = false
    setParams(
      (previous) => {
        const merged = new URLSearchParams(previous)
        merged.delete(key)
        return merged
      },
      { replace: true },
    )
  }, [key, navigate, params, setParams])

  return { value, isOpen: value != null, open, close }
}

/** A single enumerated parameter, e.g. the Insights range. Replaces rather than pushes. */
export function useUrlEnum<T extends string>(key: string, allowed: readonly T[], fallback: T) {
  const [params, setParams] = useSearchParams()
  const raw = params.get(key)
  const value = raw != null && (allowed as readonly string[]).includes(raw) ? (raw as T) : fallback

  const set = useCallback(
    (next: T) => {
      setParams(
        (previous) => {
          const merged = new URLSearchParams(previous)
          if (next === fallback) merged.delete(key)
          else merged.set(key, next)
          return merged
        },
        { replace: true },
      )
    },
    [key, fallback, setParams],
  )

  return [value, set] as const
}

/** Writes `value`, or removes the parameter entirely when it equals the default. */
function setOrDelete(params: URLSearchParams, key: string, value: string | null, fallback: string | null) {
  if (value == null || value === fallback || value === '') params.delete(key)
  else params.set(key, value)
}

/** Re-exported so a page importing this does not also need `types` for the empty case. */
export { EMPTY_CRITERIA }
