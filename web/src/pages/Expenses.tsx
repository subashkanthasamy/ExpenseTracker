import { useMemo } from 'react'

import { ExpenseForm } from '../components/ExpenseForm'
import { PageHead } from '../components/Layout'
import {
  CategoryIcon,
  Empty,
  Icon,
  Modal,
  Notice,
  Segmented,
  TableSkeleton,
  formatDate,
  useConfirmAction,
} from '../components/ui'
import { deleteExpense } from '../data/expenses'
import { useHouseholdData } from '../data/HouseholdData'
import { useSession } from '../session/SessionProvider'
import {
  canEditExpense,
  categoryOptions,
  colorOf,
  dateRangeOptions,
  filterExpenses,
  money,
  paymentMethodLabel,
  paymentMethods,
  personOptions,
} from '../shared'
import type { DateRangeWire, Expense, PaymentWire } from '../types'
import { useExpenseParams, useUrlDialog, type SortKey } from '../urlState'

/** Columns that carry the sort. The rest are read-only detail. */
const SORTABLE: Array<{ key: SortKey; label: string; numeric?: boolean; optional?: boolean }> = [
  { key: 'date', label: 'Date' },
  { key: 'category', label: 'Category' },
  { key: 'person', label: 'Added by', optional: true },
]

export function Expenses() {
  const session = useSession()
  const { expenses, categories, loading, error, canReadAll } = useHouseholdData()

  // Filters, sort and the open dialog all live in the query string, so this list is linkable
  // and Back does what a browser user expects. See `urlState.ts`.
  const { criteria, setCriteria, clearCriteria, active, sort, toggleSort } = useExpenseParams()
  const filters = useUrlDialog('filters')
  const adding = useUrlDialog('new')
  const editing = useUrlDialog('edit')
  const destructive = useConfirmAction()

  // The same predicate the Android and iOS expense screens run, so a search that matches on
  // one platform matches here — including matching against the amount.
  const matched = useMemo(() => filterExpenses(expenses, criteria), [expenses, criteria])

  const visible = useMemo(() => {
    const direction = sort.dir === 'asc' ? 1 : -1
    // Sorted on a copy: `expenses` comes straight from the Firestore listener and is shared
    // with every other page through HouseholdData.
    return [...matched].sort((a, b) => {
      switch (sort.key) {
        case 'amount':
          return (a.amount - b.amount) * direction
        case 'category':
          return a.categoryName.localeCompare(b.categoryName) * direction
        case 'person':
          return a.addedByName.localeCompare(b.addedByName) * direction
        case 'date':
          // Ties broken by creation order, so a day's rows do not shuffle between renders.
          return (a.date - b.date || a.createdAt - b.createdAt) * direction
      }
    })
  }, [matched, sort])

  const categoryChoices = useMemo(() => categoryOptions(expenses), [expenses])
  const personChoices = useMemo(() => personOptions(expenses), [expenses])
  const total = useMemo(() => visible.reduce((sum, e) => sum + e.amount, 0), [visible])

  // `?edit=<id>` can name a row that has since been deleted, or that this viewer cannot see.
  const editingExpense = editing.value != null ? expenses.find((e) => e.id === editing.value) : undefined

  const askDelete = (expense: Expense) =>
    destructive.ask({
      title: 'Delete this expense?',
      message: (
        <>
          {money(expense.amount)} · {expense.categoryName}
          {expense.notes.trim() !== '' && <> · {expense.notes}</>}. This cannot be undone.
        </>
      ),
      run: () => deleteExpense(session.household.id, expense.id),
    })

  return (
    <>
      <PageHead
        title="Expenses"
        subtitle={
          // Explicitly "all time": this sums the rows actually listed, which is every expense
          // ever rather than the current month, and unlike the dashboard it includes personal
          // rows. Left unqualified it invites comparison with the dashboard headline, which
          // measures something different on purpose.
          active
            ? `${visible.length} of ${expenses.length} shown · ${money(total)}`
            : `${expenses.length} expenses · ${money(total)} all time`
        }
      >
        {session.allows('addExpense') && (
          <button className="btn primary" type="button" onClick={() => adding.open()}>
            <Icon name="add" />
            New expense
          </button>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      {!canReadAll && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <Notice>
            You are seeing shared expenses plus your own. Other members' personal expenses are
            not shown.
          </Notice>
        </div>
      )}

      {/* One filter row above the table, so every figure below agrees with it. */}
      <div className="toolbar">
        <div className="search">
          <Icon name="search" />
          <input
            value={criteria.searchQuery}
            onChange={(event) => setCriteria({ searchQuery: event.target.value })}
            placeholder="Search notes, category, person or amount"
            aria-label="Search expenses"
          />
          {criteria.searchQuery !== '' && (
            <button
              className="btn ghost icon"
              type="button"
              aria-label="Clear search"
              onClick={() => setCriteria({ searchQuery: '' })}
            >
              <Icon name="close" size={18} />
            </button>
          )}
        </div>
        <button className="btn" type="button" onClick={() => filters.open()}>
          <Icon name="tune" />
          Filters
        </button>
        {active && (
          <button className="btn ghost" type="button" onClick={clearCriteria}>
            Clear all
          </button>
        )}
      </div>

      {active && (
        <div className="row wrap" style={{ marginBottom: 'var(--space-4)', gap: 8 }}>
          {criteria.dateRange !== 'all' && (
            <span className="chip">
              {dateRangeOptions().find((o) => o.id === criteria.dateRange)?.label}
              <button
                type="button"
                aria-label="Remove the date filter"
                onClick={() => setCriteria({ dateRange: 'all' })}
              >
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.categoryFilter != null && (
            <span className="chip">
              {categoryChoices.find((o) => o.id === criteria.categoryFilter)?.label ?? 'Category'}
              <button
                type="button"
                aria-label="Remove the category filter"
                onClick={() => setCriteria({ categoryFilter: null })}
              >
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.personFilter != null && (
            <span className="chip">
              {personChoices.find((o) => o.id === criteria.personFilter)?.label ?? 'Person'}
              <button
                type="button"
                aria-label="Remove the person filter"
                onClick={() => setCriteria({ personFilter: null })}
              >
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.paymentMethodFilter != null && (
            <span className="chip">
              {paymentMethodLabel(criteria.paymentMethodFilter)}
              <button
                type="button"
                aria-label="Remove the payment method filter"
                onClick={() => setCriteria({ paymentMethodFilter: null })}
              >
                <Icon name="close" />
              </button>
            </span>
          )}
        </div>
      )}

      {loading ? (
        <TableSkeleton rows={8} />
      ) : visible.length === 0 ? (
        <div className="panel">
          {/* The two empty states are genuinely different: nothing recorded yet, versus a
              filter that excludes everything. Showing "add your first expense" to someone
              with 60 rows and a narrow filter reads as data loss. */}
          {expenses.length === 0 ? (
            <Empty icon="receipt_long" title="No expenses yet">
              Expenses added on Android, iOS or here all show up in this list.
            </Empty>
          ) : (
            <Empty
              icon="filter_alt_off"
              title="Nothing matches those filters"
              action={
                <button className="btn" type="button" onClick={clearCriteria}>
                  Clear filters
                </button>
              }
            >
              {expenses.length} expenses are recorded, but none of them match.
            </Empty>
          )}
        </div>
      ) : (
        /*
         * A real <table>, not a stack of divs.
         *
         * This is tabular data, and the element buys three things a div list cannot: columns
         * that actually align, a header row that carries the sort affordance and announces it
         * through aria-sort, and a copy-paste that pastes as a table into a spreadsheet.
         */
        <div className="table-scroll panel">
          <table className="data-table">
            <caption className="sr-only">
              {visible.length} expenses, sorted by {sort.key}, {sort.dir === 'asc' ? 'ascending' : 'descending'}
            </caption>
            <thead>
              <tr>
                {SORTABLE.map((column) => (
                  <th
                    key={column.key}
                    scope="col"
                    className={column.optional ? 'col-optional' : undefined}
                    aria-sort={sort.key === column.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}
                  >
                    <button type="button" className="th-sort" onClick={() => toggleSort(column.key)}>
                      {column.label}
                      <Icon
                        name={
                          sort.key === column.key
                            ? sort.dir === 'asc'
                              ? 'arrow_upward'
                              : 'arrow_downward'
                            : 'unfold_more'
                        }
                        size={15}
                      />
                    </button>
                  </th>
                ))}
                <th scope="col" className="col-optional">
                  Method
                </th>
                <th scope="col" className="num-col" aria-sort={sort.key === 'amount' ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                  <button type="button" className="th-sort end" onClick={() => toggleSort('amount')}>
                    Amount
                    <Icon
                      name={
                        sort.key === 'amount'
                          ? sort.dir === 'asc'
                            ? 'arrow_upward'
                            : 'arrow_downward'
                          : 'unfold_more'
                      }
                      size={15}
                    />
                  </button>
                </th>
                {/* Header for the action column. Empty visually, named for a screen reader. */}
                <th scope="col" className="actions-col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((expense) => {
                const category = categories.find((c) => c.id === expense.categoryId)
                const editable = canEditExpense(session.role, expense, session.uid)
                return (
                  <tr key={expense.id}>
                    <td className="nowrap t-secondary">{formatDate(expense.date)}</td>
                    <td>
                      <div className="cell-primary">
                        <CategoryIcon
                          name={expense.categoryName}
                          icon={category?.icon}
                          color={category ? colorOf(category) : undefined}
                          size={26}
                        />
                        <div style={{ minWidth: 0 }}>
                          <div className="t-strong">{expense.categoryName}</div>
                          {expense.notes.trim() !== '' && (
                            <div className="t-xs t-secondary ellipsis">{expense.notes}</div>
                          )}
                        </div>
                        {expense.scope === 'personal' && <span className="badge personal">Personal</span>}
                      </div>
                    </td>
                    <td className="col-optional t-secondary nowrap">{expense.addedByName || '—'}</td>
                    <td className="col-optional t-secondary nowrap">
                      {expense.paymentMethod !== '' ? paymentMethodLabel(expense.paymentMethod) : '—'}
                    </td>
                    <td className="num-col num t-strong nowrap">{money(expense.amount)}</td>
                    <td className="actions-col">
                      {editable && (
                        <div className="actions">
                          {/* Icon is aria-hidden, so without aria-label these buttons have no
                              accessible name at all. */}
                          <button
                            className="btn ghost icon"
                            type="button"
                            aria-label={`Edit ${expense.categoryName} expense of ${money(expense.amount)}`}
                            onClick={() => editing.open(expense.id)}
                          >
                            <Icon name="edit" size={17} />
                          </button>
                          <button
                            className="btn ghost icon"
                            type="button"
                            aria-label={`Delete ${expense.categoryName} expense of ${money(expense.amount)}`}
                            onClick={() => askDelete(expense)}
                          >
                            <Icon name="delete" size={17} />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {filters.isOpen && (
        <Modal
          title="Filters"
          subtitle="These narrow the list only. Nothing here changes what is stored."
          onClose={filters.close}
          actions={
            <>
              <button className="btn ghost" type="button" onClick={clearCriteria}>
                Clear all
              </button>
              <button className="btn primary" type="button" onClick={filters.close}>
                Done
              </button>
            </>
          }
        >
          <div className="field">
            <span>Date range</span>
            <Segmented
              options={dateRangeOptions().map((option) => ({
                value: option.id as DateRangeWire,
                label: option.label,
              }))}
              value={criteria.dateRange}
              onChange={(value) => setCriteria({ dateRange: value })}
            />
          </div>

          <div className="field">
            <span>Payment method</span>
            <Segmented
              options={[
                { value: null, label: 'Any' },
                ...paymentMethods().map((option) => ({ value: option.wire, label: option.label })),
              ]}
              value={criteria.paymentMethodFilter as PaymentWire | null}
              onChange={(value) => setCriteria({ paymentMethodFilter: value })}
            />
          </div>

          <label className="field">
            <span>Category</span>
            <select
              value={criteria.categoryFilter ?? ''}
              onChange={(event) => setCriteria({ categoryFilter: event.target.value || null })}
            >
              <option value="">Any category</option>
              {categoryChoices.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>Person</span>
            <select
              value={criteria.personFilter ?? ''}
              onChange={(event) => setCriteria({ personFilter: event.target.value || null })}
            >
              <option value="">Anyone</option>
              {personChoices.map((option) => (
                <option key={option.id} value={option.id}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </Modal>
      )}

      {adding.isOpen && <ExpenseForm categories={categories} onClose={adding.close} />}

      {/* A stale `?edit=<id>` — a shared link to a row since deleted, or one this viewer
          cannot see — falls through to nothing rather than rendering a blank form. */}
      {editingExpense != null && (
        <ExpenseForm categories={categories} existing={editingExpense} onClose={editing.close} />
      )}

      {destructive.node}
    </>
  )
}
