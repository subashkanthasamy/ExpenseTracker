import { useCallback, useMemo, useState } from 'react'

import { PageHead } from '../components/Layout'
import { Card, Empty, Field, Icon, Modal, Notice, Progress, Spinner } from '../components/ui'
import { deleteBudget, observeBudgets, upsertBudget } from '../data/budgets'
import { useCollection, useHouseholdData } from '../data/HouseholdData'
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

  /**
   * Spend per category for the current month, from shared rows only.
   *
   * Personal rows are excluded so a budget reads the same for every member — otherwise the
   * owner would see a category over budget that a member sees as under.
   */
  const spentByCategory = useMemo(() => {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 1).getTime()
    const totals = new Map<string, number>()
    sharedOnly(expenses)
      .filter((expense) => expense.date >= start && expense.date < end)
      .forEach((expense) => {
        totals.set(expense.categoryId, (totals.get(expense.categoryId) ?? 0) + expense.amount)
      })
    return totals
  }, [expenses])

  const remove = async (budget: Budget) => {
    if (!window.confirm(`Remove the budget for ${budget.categoryName}?`)) return
    try {
      await deleteBudget(householdId, budget.id)
    } catch (caught) {
      window.alert((caught as Error)?.message ?? 'Could not delete.')
    }
  }

  return (
    <>
      <PageHead title="Budgets" subtitle="Monthly limits per category, measured against shared spending.">
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
            Budgets are set by the household owner. You can see them but not change them.
          </Notice>
        </div>
      )}

      {loading ? (
        <Card>
          <Spinner label="Loading budgets…" />
        </Card>
      ) : budgets.length === 0 ? (
        <Card>
          <Empty
            icon="savings"
            title="No budgets yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => setCreating(true)}>
                  Create the first one
                </button>
              ) : undefined
            }
          >
            A budget sets a monthly ceiling for one category and warns at 80%.
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
                    <strong style={{ fontSize: 15 }}>{budget.categoryName}</strong>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 3 }}>
                      {money(spent)} of {money(budget.monthlyLimit)}
                    </div>
                  </div>
                  <div className="row" style={{ gap: 6 }}>
                    <span
                      style={{
                        fontWeight: 700,
                        fontSize: 15,
                        color:
                          progress.status === 'exceeded'
                            ? 'var(--expense)'
                            : progress.status === 'warning'
                              ? 'var(--warning)'
                              : 'var(--income)',
                      }}
                    >
                      {progress.percentage.toFixed(0)}%
                    </span>
                    {canManage && (
                      <>
                        <button className="btn ghost icon" type="button" onClick={() => setEditing(budget)}>
                          <Icon name="edit" size={17} />
                        </button>
                        <button className="btn ghost icon" type="button" onClick={() => void remove(budget)}>
                          <Icon name="delete" size={17} />
                        </button>
                      </>
                    )}
                  </div>
                </div>
                <Progress value={progress.percentage / 100} status={progress.status} />
                {progress.status === 'exceeded' && (
                  <div style={{ fontSize: 12, color: 'var(--expense)', marginTop: 8 }}>
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
      setError('Pick a category and a limit above zero.')
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
      setError((caught as Error)?.message ?? 'Could not save.')
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
        <Field label="Monthly limit" hint={parsed > 0 ? money(parsed) : undefined}>
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
