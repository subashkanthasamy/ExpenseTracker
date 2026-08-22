import { useCallback, useState } from 'react'

import { PageHead } from '../components/Layout'
import { Card, Empty, Field, Icon, Modal, Notice, Progress, Spinner, Stat, formatDate, fromDateInput, toDateInput } from '../components/ui'
import { deleteGoal, observeGoals, upsertGoal } from '../data/goals'
import { useCollection } from '../data/HouseholdData'
import { useSession } from '../session/SessionProvider'
import { goalProgress, money } from '../shared'
import type { SavingsGoal } from '../types'

export function Savings() {
  const session = useSession()
  const householdId = session.household.id
  const canManage = session.allows('manageSharedConfig')

  const subscribe = useCallback(
    (onChange: (rows: SavingsGoal[]) => void, onError: (error: Error) => void) =>
      observeGoals(householdId, onChange, onError),
    [householdId],
  )
  const { rows: goals, loading, error } = useCollection<SavingsGoal>(subscribe, [householdId])

  const [editing, setEditing] = useState<SavingsGoal | null>(null)
  const [creating, setCreating] = useState(false)

  const totalTarget = goals.reduce((sum, goal) => sum + goal.targetAmount, 0)
  const totalSaved = goals.reduce((sum, goal) => sum + goal.currentAmount, 0)

  const remove = async (goal: SavingsGoal) => {
    if (!window.confirm(`Delete the goal "${goal.name}"?`)) return
    try {
      await deleteGoal(householdId, goal.id)
    } catch (caught) {
      window.alert((caught as Error)?.message ?? 'Could not delete.')
    }
  }

  return (
    <>
      <PageHead title="Savings goals" subtitle="What the household is putting money aside for.">
        {canManage && (
          <button className="btn primary" type="button" onClick={() => setCreating(true)}>
            <Icon name="add" />
            New goal
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
          <Notice>Savings goals are managed by the household owner.</Notice>
        </div>
      )}

      {goals.length > 0 && (
        <div className="grid cols-3" style={{ marginBottom: 20 }}>
          <Stat label="Saved so far" value={money(totalSaved)} accent="var(--income)" />
          <Stat label="Total target" value={money(totalTarget)} />
          <Stat
            label="Still to go"
            value={money(Math.max(0, totalTarget - totalSaved))}
            sub={`${goals.length} goal${goals.length === 1 ? '' : 's'}`}
          />
        </div>
      )}

      {loading ? (
        <Card>
          <Spinner label="Loading goals…" />
        </Card>
      ) : goals.length === 0 ? (
        <Card>
          <Empty
            icon="flag"
            title="No savings goals yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => setCreating(true)}>
                  Create the first one
                </button>
              ) : undefined
            }
          >
            A goal tracks progress toward a target, and works out what to set aside each month.
          </Empty>
        </Card>
      ) : (
        <div className="grid cols-2">
          {goals.map((goal) => {
            // progress / remaining / monthlyNeeded all come from the shared SavingsGoal model.
            const progress = goalProgress(goal.currentAmount, goal.targetAmount, goal.targetDate)
            return (
              <Card key={goal.id}>
                <div className="row between" style={{ marginBottom: 10 }}>
                  <div className="row" style={{ gap: 10 }}>
                    <span style={{ fontSize: 22 }}>{goal.icon}</span>
                    <div>
                      <strong style={{ fontSize: 15 }}>{goal.name}</strong>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 3 }}>
                        {money(goal.currentAmount)} of {money(goal.targetAmount)}
                      </div>
                    </div>
                  </div>
                  {canManage && (
                    <div className="row" style={{ gap: 4 }}>
                      <button className="btn ghost icon" type="button" onClick={() => setEditing(goal)}>
                        <Icon name="edit" size={17} />
                      </button>
                      <button className="btn ghost icon" type="button" onClick={() => void remove(goal)}>
                        <Icon name="delete" size={17} />
                      </button>
                    </div>
                  )}
                </div>

                <Progress value={progress.progress} />

                <div className="row between" style={{ marginTop: 10, fontSize: 12 }}>
                  <span style={{ color: 'var(--text-secondary)' }}>
                    {(progress.progress * 100).toFixed(0)}% ·{' '}
                    {progress.remaining > 0 ? `${money(progress.remaining)} to go` : 'Reached'}
                  </span>
                  {goal.targetDate != null && (
                    <span style={{ color: 'var(--text-tertiary)' }}>by {formatDate(goal.targetDate)}</span>
                  )}
                </div>

                {progress.monthlyNeeded != null && (
                  <div style={{ marginTop: 10, fontSize: 13 }}>
                    Set aside <strong>{money(progress.monthlyNeeded)}</strong> a month to hit the date.
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {(creating || editing != null) && (
        <GoalForm
          householdId={householdId}
          existing={editing ?? undefined}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </>
  )
}

function GoalForm({
  householdId,
  existing,
  onClose,
}: {
  householdId: string
  existing?: SavingsGoal
  onClose: () => void
}) {
  const [name, setName] = useState(existing?.name ?? '')
  const [target, setTarget] = useState(existing ? String(existing.targetAmount) : '')
  const [current, setCurrent] = useState(existing ? String(existing.currentAmount) : '0')
  const [icon, setIcon] = useState(existing?.icon ?? '🎯')
  const [hasDate, setHasDate] = useState(existing?.targetDate != null)
  const [date, setDate] = useState(toDateInput(existing?.targetDate ?? Date.now()))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const parsedTarget = Number(target)
  const parsedCurrent = Number(current)
  const valid =
    name.trim() !== '' &&
    Number.isFinite(parsedTarget) &&
    parsedTarget > 0 &&
    Number.isFinite(parsedCurrent) &&
    parsedCurrent >= 0

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError('Give the goal a name and a target above zero.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await upsertGoal({
        id: existing?.id ?? crypto.randomUUID(),
        householdId,
        name: name.trim(),
        targetAmount: parsedTarget,
        currentAmount: parsedCurrent,
        icon,
        targetDate: hasDate ? fromDateInput(date) : null,
        createdAt: existing?.createdAt ?? Date.now(),
      })
      onClose()
    } catch (caught) {
      setError((caught as Error)?.message ?? 'Could not save.')
      setBusy(false)
    }
  }

  return (
    <Modal
      title={existing ? 'Edit goal' : 'New savings goal'}
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="goal-form" disabled={busy || !valid}>
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
      <form id="goal-form" onSubmit={submit}>
        <div className="row" style={{ gap: 12, alignItems: 'flex-start' }}>
          <div style={{ width: 76 }}>
            <Field label="Icon">
              <input
                value={icon}
                onChange={(event) => setIcon(event.target.value.slice(0, 2))}
                style={{ textAlign: 'center', fontSize: 20 }}
              />
            </Field>
          </div>
          <div style={{ flex: 1 }}>
            <Field label="Name">
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="e.g. Emergency fund"
                maxLength={80}
                autoFocus
                required
              />
            </Field>
          </div>
        </div>

        <Field label="Target amount" hint={parsedTarget > 0 ? money(parsedTarget) : undefined}>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={target}
            onChange={(event) => setTarget(event.target.value)}
            required
          />
        </Field>

        <Field label="Saved so far">
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
            required
          />
        </Field>

        <div className="field">
          <span>Target date</span>
          <div className="row" style={{ gap: 10 }}>
            <input
              type="checkbox"
              checked={hasDate}
              onChange={(event) => setHasDate(event.target.checked)}
              style={{ width: 'auto' }}
            />
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              disabled={!hasDate}
            />
          </div>
          <div className="field-hint">With a date, the monthly amount needed is worked out for you.</div>
        </div>
      </form>
    </Modal>
  )
}
