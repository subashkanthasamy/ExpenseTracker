import { useCallback, useState } from 'react'

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
  Stat,
  formatDate,
  fromDateInput,
  toDateInput,
  useConfirmAction,
} from '../components/ui'
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
  const destructive = useConfirmAction()

  const totalTarget = goals.reduce((sum, goal) => sum + goal.targetAmount, 0)
  const totalSaved = goals.reduce((sum, goal) => sum + goal.currentAmount, 0)

  const remove = (goal: SavingsGoal) =>
    destructive.ask({
      title: `Delete "${goal.name}"?`,
      message: `${money(goal.currentAmount)} of ${money(goal.targetAmount)} saved. Your expenses aren't affected. This can't be undone.`,
      run: () => deleteGoal(householdId, goal.id),
    })

  return (
    <>
      <PageHead title="Savings goals" subtitle="What your household is saving for">
        {canManage && (
          <button className="btn primary" type="button" onClick={() => setCreating(true)}>
            <Icon name="add" />
            New savings goal
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
          <Notice>Only the household owner and admins can change savings goals.</Notice>
        </div>
      )}

      {goals.length > 0 && (
        <div className="grid cols-3" style={{ marginBottom: 20 }}>
          <Stat label="Saved so far" value={money(totalSaved)} accent="var(--income-text)" />
          <Stat label="Total target" value={money(totalTarget)} />
          <Stat
            label="Still to go"
            value={money(Math.max(0, totalTarget - totalSaved))}
            sub={`${goals.length} goal${goals.length === 1 ? '' : 's'}`}
          />
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
      ) : goals.length === 0 ? (
        <Card>
          <Empty
            icon="flag"
            title="No savings goals yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => setCreating(true)}>
                  Add savings goal
                </button>
              ) : undefined
            }
          >
            A savings goal tracks your progress and works out how much to set aside each month.
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
                      <strong className="t-lg">{goal.name}</strong>
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 3 }}>
                        {money(goal.currentAmount)} of {money(goal.targetAmount)}
                      </div>
                    </div>
                  </div>
                  {canManage && (
                    <div className="row" style={{ gap: 4 }}>
                      <button
                        className="btn ghost icon"
                        type="button"
                        aria-label={`Edit ${goal.name} savings goal`}
                        onClick={() => setEditing(goal)}
                      >
                        <Icon name="edit" size={17} />
                      </button>
                      <button
                        className="btn ghost icon"
                        type="button"
                        aria-label={`Delete ${goal.name} savings goal`}
                        onClick={() => void remove(goal)}
                      >
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
                    Set aside <strong>{money(progress.monthlyNeeded)}</strong> a month to reach it by the target date.
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

      {destructive.node}
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
      setError('Enter a name and a target amount greater than zero.')
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
      setError(`Couldn't save the savings goal. ${(caught as Error)?.message ?? 'Check your connection and try again.'}`)
      setBusy(false)
    }
  }

  return (
    <Modal
      title={existing ? 'Edit savings goal' : 'New savings goal'}
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
                placeholder="For example, Emergency fund"
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
              aria-label="Set a target date"
              style={{ width: 'auto' }}
            />
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              disabled={!hasDate}
            />
          </div>
          <div className="field-hint">Add a date to see how much to set aside each month.</div>
        </div>
      </form>
    </Modal>
  )
}
