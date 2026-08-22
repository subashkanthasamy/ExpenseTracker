import { useMemo, useState } from 'react'

import { PageHead } from '../components/Layout'
import { ExpenseForm } from '../components/ExpenseForm'
import {
  Card,
  CategoryIcon,
  Empty,
  Icon,
  Modal,
  Notice,
  Segmented,
  Spinner,
  formatDate,
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
import { EMPTY_CRITERIA, type DateRangeWire, type Expense, type FilterCriteria, type PaymentWire } from '../types'

export function Expenses() {
  const session = useSession()
  const { expenses, categories, loading, error, canReadAll } = useHouseholdData()

  const [criteria, setCriteria] = useState<FilterCriteria>(EMPTY_CRITERIA)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [editing, setEditing] = useState<Expense | null>(null)
  const [adding, setAdding] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

  // The same predicate the Android and iOS expense screens run, so a search that matches on
  // one platform matches here — including matching against the amount.
  const visible = useMemo(() => filterExpenses(expenses, criteria), [expenses, criteria])

  const categoryChoices = useMemo(() => categoryOptions(expenses), [expenses])
  const personChoices = useMemo(() => personOptions(expenses), [expenses])
  const total = useMemo(() => visible.reduce((sum, e) => sum + e.amount, 0), [visible])

  const active =
    criteria.searchQuery.trim() !== '' ||
    criteria.personFilter != null ||
    criteria.categoryFilter != null ||
    criteria.paymentMethodFilter != null ||
    criteria.dateRange !== 'all'

  const remove = async (expense: Expense) => {
    if (!window.confirm('Delete this expense? This cannot be undone.')) return
    setBusyId(expense.id)
    try {
      await deleteExpense(session.household.id, expense.id)
    } catch (caught) {
      window.alert((caught as Error)?.message ?? 'Could not delete.')
    } finally {
      setBusyId(null)
    }
  }

  const patch = (next: Partial<FilterCriteria>) => setCriteria({ ...criteria, ...next })

  return (
    <>
      <PageHead
        title="Expenses"
        subtitle={
          active
            ? `${visible.length} of ${expenses.length} · ${money(total)}`
            : `${expenses.length} expenses · ${money(total)}`
        }
      >
        {session.allows('addExpense') && (
          <button className="btn primary" type="button" onClick={() => setAdding(true)}>
            <Icon name="add" />
            Add expense
          </button>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      {!canReadAll && (
        <div style={{ marginBottom: 16 }}>
          <Notice>
            You are seeing shared expenses plus your own. Other members' personal expenses are
            not shown.
          </Notice>
        </div>
      )}

      <div className="row wrap" style={{ marginBottom: 16 }}>
        <div className="search">
          <Icon name="search" />
          <input
            value={criteria.searchQuery}
            onChange={(event) => patch({ searchQuery: event.target.value })}
            placeholder="Search notes, category, person or amount"
          />
          {criteria.searchQuery !== '' && (
            <button className="btn ghost icon" type="button" onClick={() => patch({ searchQuery: '' })}>
              <Icon name="close" size={18} />
            </button>
          )}
        </div>
        <button className="btn" type="button" onClick={() => setSheetOpen(true)}>
          <Icon name="tune" />
          Filters
        </button>
        {active && (
          <button className="btn ghost" type="button" onClick={() => setCriteria(EMPTY_CRITERIA)}>
            Clear all
          </button>
        )}
      </div>

      {active && (
        <div className="row wrap" style={{ marginBottom: 16, gap: 8 }}>
          {criteria.dateRange !== 'all' && (
            <span className="chip">
              {dateRangeOptions().find((o) => o.id === criteria.dateRange)?.label}
              <button type="button" onClick={() => patch({ dateRange: 'all' })}>
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.categoryFilter != null && (
            <span className="chip">
              {categoryChoices.find((o) => o.id === criteria.categoryFilter)?.label ?? 'Category'}
              <button type="button" onClick={() => patch({ categoryFilter: null })}>
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.personFilter != null && (
            <span className="chip">
              {personChoices.find((o) => o.id === criteria.personFilter)?.label ?? 'Person'}
              <button type="button" onClick={() => patch({ personFilter: null })}>
                <Icon name="close" />
              </button>
            </span>
          )}
          {criteria.paymentMethodFilter != null && (
            <span className="chip">
              {paymentMethodLabel(criteria.paymentMethodFilter)}
              <button type="button" onClick={() => patch({ paymentMethodFilter: null })}>
                <Icon name="close" />
              </button>
            </span>
          )}
        </div>
      )}

      <Card>
        {loading ? (
          <Spinner label="Loading expenses…" />
        ) : visible.length === 0 ? (
          // The two empty states are genuinely different: nothing recorded yet, versus a
          // filter that excludes everything. Showing "add your first expense" to someone with
          // 60 rows and a narrow filter reads as data loss.
          expenses.length === 0 ? (
            <Empty icon="receipt_long" title="No expenses yet">
              Expenses added on Android, iOS or here all show up in this list.
            </Empty>
          ) : (
            <Empty
              icon="filter_alt_off"
              title="Nothing matches those filters"
              action={
                <button className="btn" type="button" onClick={() => setCriteria(EMPTY_CRITERIA)}>
                  Clear filters
                </button>
              }
            >
              {expenses.length} expenses are hidden by the current filters.
            </Empty>
          )
        ) : (
          <div className="list">
            {visible.map((expense) => {
              const category = categories.find((c) => c.id === expense.categoryId)
              const editable = canEditExpense(session.role, expense, session.uid)
              return (
                <div className="list-row" key={expense.id}>
                  <CategoryIcon
                    name={expense.categoryName}
                    icon={category?.icon}
                    color={category ? colorOf(category) : undefined}
                  />
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div className="title">
                      {expense.notes.trim() !== '' ? expense.notes : expense.categoryName}
                    </div>
                    <div className="meta">
                      <span>{formatDate(expense.date)}</span>
                      <span>·</span>
                      <span>{expense.categoryName}</span>
                      {expense.addedByName.trim() !== '' && (
                        <>
                          <span>·</span>
                          <span>{expense.addedByName}</span>
                        </>
                      )}
                      {expense.paymentMethod !== '' && (
                        <>
                          <span>·</span>
                          <span>{paymentMethodLabel(expense.paymentMethod)}</span>
                        </>
                      )}
                      {expense.scope === 'personal' && <span className="badge personal">Personal</span>}
                    </div>
                  </div>
                  <div className="amount">{money(expense.amount)}</div>
                  {editable && (
                    <div className="actions">
                      <button className="btn ghost" type="button" onClick={() => setEditing(expense)}>
                        <Icon name="edit" size={17} />
                      </button>
                      <button
                        className="btn ghost"
                        type="button"
                        disabled={busyId === expense.id}
                        onClick={() => void remove(expense)}
                      >
                        <Icon name="delete" size={17} />
                      </button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </Card>

      {sheetOpen && (
        <Modal
          title="Filters"
          onClose={() => setSheetOpen(false)}
          actions={
            <>
              <button className="btn ghost" type="button" onClick={() => setCriteria(EMPTY_CRITERIA)}>
                Reset
              </button>
              <button className="btn primary" type="button" onClick={() => setSheetOpen(false)}>
                Done
              </button>
            </>
          }
        >
          <div className="field">
            <span>Date range</span>
            <Segmented
              options={dateRangeOptions().map((o) => ({ value: o.id as DateRangeWire, label: o.label }))}
              value={criteria.dateRange}
              onChange={(dateRange) => patch({ dateRange })}
            />
          </div>

          <div className="field">
            <span>Payment method</span>
            <Segmented<PaymentWire | null>
              options={[
                { value: null, label: 'Any' },
                ...paymentMethods().map((m) => ({ value: m.wire as PaymentWire | null, label: m.label })),
              ]}
              value={criteria.paymentMethodFilter}
              onChange={(paymentMethodFilter) => patch({ paymentMethodFilter })}
            />
          </div>

          <label className="field">
            <span>Category</span>
            <select
              value={criteria.categoryFilter ?? ''}
              onChange={(event) => patch({ categoryFilter: event.target.value || null })}
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
              onChange={(event) => patch({ personFilter: event.target.value || null })}
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

      {adding && <ExpenseForm categories={categories} onClose={() => setAdding(false)} />}
      {editing != null && (
        <ExpenseForm
          categories={categories}
          existing={editing}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  )
}
