/**
 * Live expenses and categories for the active household, subscribed once and shared.
 *
 * Several pages need both, and each opening its own listener would multiply the read cost and
 * make the two-query member path run repeatedly.
 */
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { useSession } from '../session/SessionProvider'
import type { Category, Expense } from '../types'
import { observeCategories, topUpPresetCategories } from './categories'
import { observeExpenses } from './expenses'

interface HouseholdData {
  expenses: Expense[]
  categories: Category[]
  loading: boolean
  /** Set when a listener was rejected. Distinct from "no data". */
  error: string | null
  /** True while a member could still be missing rows — see the note in `observeExpenses`. */
  canReadAll: boolean
}

const Context = createContext<HouseholdData | null>(null)

export function HouseholdDataProvider({ children }: { children: ReactNode }) {
  const session = useSession()
  const householdId = session.household.id
  const canReadAll = session.allows('readAllExpenses')

  const [expenses, setExpenses] = useState<Expense[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [expensesLoaded, setExpensesLoaded] = useState(false)
  const [categoriesLoaded, setCategoriesLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setExpensesLoaded(false)
    setError(null)

    const unsubscribe = observeExpenses(
      householdId,
      canReadAll,
      session.uid,
      (rows) => {
        setExpenses(rows)
        setExpensesLoaded(true)
      },
      (listenerError) => {
        // A rejected query is the failure mode to name explicitly: it presents as an empty
        // list, and the usual cause is an expense row with no `scope` field.
        setError(
          `Couldn't load expenses. Refresh the page to try again. (${listenerError.message})`,
        )
        setExpensesLoaded(true)
      },
    )
    return unsubscribe
  }, [householdId, canReadAll, session.uid])

  useEffect(() => {
    setCategoriesLoaded(false)
    const unsubscribe = observeCategories(
      householdId,
      (rows) => {
        setCategories(rows)
        setCategoriesLoaded(true)
      },
      (listenerError) => {
        setError(`Couldn't load categories. Refresh the page to try again. (${listenerError.message})`)
        setCategoriesLoaded(true)
      },
    )
    return unsubscribe
  }, [householdId])

  // Preset top-up, once per session per household, and only for a role the rules permit to
  // write shared config. A member attempting it would simply be denied.
  const toppedUp = useRef<string | null>(null)
  useEffect(() => {
    if (!session.allows('manageSharedConfig')) return
    if (toppedUp.current === householdId) return
    toppedUp.current = householdId
    void topUpPresetCategories(session.household).catch(() => {
      /* Non-fatal: the household keeps whatever categories it already has. */
    })
  }, [householdId, session])

  const value = useMemo<HouseholdData>(
    () => ({
      expenses,
      categories,
      loading: !expensesLoaded || !categoriesLoaded,
      error,
      canReadAll,
    }),
    [expenses, categories, expensesLoaded, categoriesLoaded, error, canReadAll],
  )

  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useHouseholdData(): HouseholdData {
  const value = useContext(Context)
  if (!value) throw new Error('useHouseholdData() outside HouseholdDataProvider')
  return value
}

/** Live subscription helper for the smaller collections, each used by a single page. */
export function useCollection<T>(
  subscribe: (
    onChange: (rows: T[]) => void,
    onError: (error: Error) => void,
  ) => () => void,
  deps: unknown[],
): { rows: T[]; loading: boolean; error: string | null } {
  const [rows, setRows] = useState<T[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    const unsubscribe = subscribe(
      (next) => {
        setRows(next)
        setLoading(false)
      },
      (subscribeError) => {
        setError(`Couldn't load this page. Refresh to try again. (${subscribeError.message})`)
        setLoading(false)
      },
    )
    return unsubscribe
    // The caller owns the identity of `subscribe`; deps are declared explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { rows, loading, error }
}
