import { useCallback, useMemo, useState } from 'react'

import { PageHead } from '../components/Layout'
import {
  Card,
  Empty,
  Field,
  Icon,
  ListSkeleton,
  Modal,
  Notice,
  Progress,
  useConfirmAction,
} from '../components/ui'
import { deleteBudget, observeBudgets, upsertBudget } from '../data/budgets'
import { useCollection, useHouseholdData } from '../data/HouseholdData'
import { monthRowsByCategory } from '../data/series'
import { useSession } from '../session/SessionProvider'
import { budgetProgress, money, sharedOnly } from '../shared'
import type { Budget } from '../types'

export function Budgets() {
  const session = useSession()
  const householdId = session.household.id
  const canManage = session.allows('manageSharedConfig')
  const { expenses, categories } = useHouseholdData()

  const subscribe = useCallback(
    (onChange: (rows: Budget[]) => void, onError: (error: Error) => void) =>
      observeBudgets(householdId, onChange, onError),
    [householdId],
  )
  const { rows: budgets, loading, error } = useCollection<Budget>(subscribe, [householdId])

  const [editing, setEditing] = useState<Budget | null>(null)
  const [creating, setCreating] = useState(false)
  const destructive = useConfirmAction()

  /**
   * Spend per category for the current month, from shared rows only.
   *
   * Personal rows are excluded so a budget reads the same for every member — otherwise the
   * owner would see a category over budget that a member sees as under.
   */
  const spentByCategory = useMemo(() => {
    const totals = new Map<string, number>()
    monthRowsByCategory(sharedOnly(expenses)).forEach((rows, categoryId) => {
      totals.set(categoryId, rows.reduce((sum, expense) => sum + expense.amount, 0))
    })
    return totals
  }, [expenses])

  const remove = (budget: Budget) =>
    destructive.ask({
      title: 'Delete this budget?',
      message: `${budget.categoryName} will no longer have a monthly budget. Your expenses aren't affected.`,
      run: () => deleteBudget(householdId, budget.id),
    })

  return (
    <>
      <PageHead title="Budgets" subtitle="Monthly budgets by category, tracked against shared spending">
        {canManage && (
          <button className="btn primary" type="button" onClick={() => setCreating(true)}>
            <Icon name="add" />
            New budget
          </button>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      {!canManage && (
        <div style={{ marginBottom: 16 }}>
          <Notice>
            Only the household owner and admins can change budgets.
          </Notice>
        </div>
      )}

      {loading ? (
        <div className="grid cols-2">
          <Card>
            <ListSkeleton rows={2} />
          </Card>
          <Card>
            <ListSkeleton rows={2} />
          </Card>
        </div>
      ) : budgets.length === 0 ? (
        <Card>
          <Empty
            icon="savings"
            title="No budgets yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => setCreating(true)}>
                  Add budget
                </button>
              ) : undefined
            }
          >
            A budget tracks a category's spending each month and warns you at 80%.
          </Empty>
        </Card>
      ) : (
        <div className="grid cols-2">
          {budgets.map((budget) => {
            const spent = spentByCategory.get(budget.categoryId) ?? 0
            // Thresholds come from the shared Budget model, so "warning" means the same
            // thing here as on Android and iOS.
            const progress = budgetProgress(spent, budget.monthlyLimit)
            return (
              <Card key={budget.id}>
                <div className="row between" style={{ marginBottom: 10 }}>
                  <div>
                    <strong className="t-lg">{budget.categoryName}</strong>
                    <div className="t-xs t-secondary" style={{ marginTop: 3 }}>
                      {money(spent)} of {money(budget.monthlyLimit)}
                    </div>
                  </div>
                  <div className="row" style={{ gap: 6 }}>
                    {/* The -text tokens, not --expense / --warning / --income: those are
                        fill colours and measure 1.8-3.7:1 as text on a light surface.
                        --warning at 1.80 was the worst offender, and it was here. */}
                    <span
                      className="t-lg t-strong num"
                      style={{
                        fontWeight: 700,
                        color:
                          progress.status === 'exceeded'
                            ? 'var(--expense-text)'
                            : progress.status === 'warning'
                              ? 'var(--warning-text)'
                              : 'var(--income-text)',
                      }}
                    >
                      {progress.percentage.toFixed(0)}%
                    </span>
                    {canManage && (
                      <>
                        <button
                          className="btn ghost icon"
                          type="button"
                          aria-label={`Edit ${budget.categoryName} budget`}
                          onClick={() => setEditing(budget)}
                        >
                          <Icon name="edit" size={17} />
                        </button>
                        <button
                          className="btn ghost icon"
                          type="button"
                          aria-label={`Delete ${budget.categoryName} budget`}
                          onClick={() => void remove(budget)}
                        >
                          <Icon name="delete" size={17} />
                        </button>
                      </>
                    )}
                  </div>
                </div>
                <Progress value={progress.percentage / 100} status={progress.status} />
                {progress.status === 'exceeded' && (
                  <div style={{ fontSize: 12, color: 'var(--expense-text)', marginTop: 8 }}>
                    Over by {money(spent - budget.monthlyLimit)}
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {(creating || editing != null) && (
        <BudgetForm
          householdId={householdId}
          categories={categories.map((c) => ({ id: c.id, name: c.name }))}
          existing={editing ?? undefined}
          taken={budgets.map((b) => b.categoryId)}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      {destructive.node}
    </>
  )
}

function BudgetForm({
  householdId,
  categories,
  existing,
  taken,
  onClose,
}: {
  householdId: string
  categories: Array<{ id: string; name: string }>
  existing?: Budget
  taken: string[]
  onClose: () => void
}) {
  // A category already budgeted is not offered again — two budgets for one category would
  // both look authoritative and disagree.
  const available = categories.filter((c) => c.id === existing?.categoryId || !taken.includes(c.id))

  const [categoryId, setCategoryId] = useState(existing?.categoryId ?? available[0]?.id ?? '')
  const [limit, setLimit] = useState(existing ? String(existing.monthlyLimit) : '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const parsed = Number(limit)
  const valid = categoryId !== '' && Number.isFinite(parsed) && parsed > 0

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError('Choose a category and enter a budget greater than zero.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await upsertBudget({
        id: existing?.id ?? `${categoryId}`,
        householdId,
        categoryId,
        categoryName: categories.find((c) => c.id === categoryId)?.name ?? '',
        monthlyLimit: parsed,
      })
      onClose()
    } catch (caught) {
      setError(`Couldn't save the budget. ${(caught as Error)?.message ?? 'Check your connection and try again.'}`)
      setBusy(false)
    }
  }

  return (
    <Modal
      title={existing ? 'Edit budget' : 'New budget'}
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="budget-form" disabled={busy || !valid}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      {error !== '' && (
        <div style={{ marginBottom: 14 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}
      <form id="budget-form" onSubmit={submit}>
        <Field label="Category">
          <select
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            disabled={existing != null}
            required
          >
            {available.length === 0 && <option value="">Every category already has a budget</option>}
            {available.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Monthly budget" hint={parsed > 0 ? money(parsed) : undefined}>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={limit}
            onChange={(event) => setLimit(event.target.value)}
            autoFocus
            required
          />
        </Field>
      </form>
    </Modal>
  )
}
