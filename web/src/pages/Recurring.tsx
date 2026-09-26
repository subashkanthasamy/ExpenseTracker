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
  Segmented,
  formatDate,
  fromDateInput,
  toDateInput,
  useConfirmAction,
  useErrorToast,
} from '../components/ui'
import { useCollection, useHouseholdData } from '../data/HouseholdData'
import { FREQUENCY_LABELS, deleteRecurring, observeRecurring, upsertRecurring } from '../data/recurring'
import { useSession } from '../session/SessionProvider'
import { money, paymentMethods } from '../shared'
import type { PaymentWire, RecurringExpense, RecurringFrequency } from '../types'

export function Recurring() {
  const session = useSession()
  const householdId = session.household.id
  const canManage = session.allows('manageSharedConfig')
  const { categories } = useHouseholdData()

  const subscribe = useCallback(
    (onChange: (rows: RecurringExpense[]) => void, onError: (error: Error) => void) =>
      observeRecurring(householdId, onChange, onError),
    [householdId],
  )
  const { rows, loading, error } = useCollection<RecurringExpense>(subscribe, [householdId])

  const [editing, setEditing] = useState<RecurringExpense | null>(null)
  const [creating, setCreating] = useState(false)
  const destructive = useConfirmAction()
  // Pausing has no confirmation step, so it needs the toast on its own.
  const errorToast = useErrorToast()

  const toggleActive = async (rule: RecurringExpense) => {
    try {
      await upsertRecurring({ ...rule, isActive: !rule.isActive })
    } catch (caught) {
      errorToast.show(caught)
    }
  }

  return (
    <>
      <PageHead title="Recurring" subtitle="Expenses that repeat automatically">
        {canManage && (
          <button className="btn primary" type="button" onClick={() => setCreating(true)}>
            <Icon name="add" />
            New recurring expense
          </button>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <div style={{ marginBottom: 16 }}>
        {/* Being explicit about this avoids the obvious support question, and about a real
            design decision rather than a missing feature. */}
        <Notice>
          You can set up recurring expenses here. Each one is added to your expenses the next time
          someone in your household opens the Android or iOS app.
        </Notice>
      </div>

      {!canManage && (
        <div style={{ marginBottom: 16 }}>
          <Notice>Only the household owner and admins can change recurring expenses.</Notice>
        </div>
      )}

      {loading ? (
        <Card>
          <ListSkeleton rows={3} />
        </Card>
      ) : rows.length === 0 ? (
        <Card>
          <Empty
            icon="autorenew"
            title="No recurring expenses yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => setCreating(true)}>
                  Add recurring expense
                </button>
              ) : undefined
            }
          >
            Use these for rent, the milk bill or anything else that repeats.
          </Empty>
        </Card>
      ) : (
        <Card>
          <div className="list">
            {rows.map((rule) => (
              <div className="list-row" key={rule.id}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="title">
                    {rule.notes.trim() !== '' ? rule.notes : rule.categoryName}
                    {!rule.isActive && (
                      <span className="badge" style={{ marginLeft: 8 }}>
                        Paused
                      </span>
                    )}
                  </div>
                  <div className="meta">
                    <span>{FREQUENCY_LABELS[rule.frequency]}</span>
                    <span>·</span>
                    <span>{rule.categoryName}</span>
                    <span>·</span>
                    <span>from {formatDate(rule.startDate)}</span>
                    {rule.lastGeneratedDate != null && (
                      <>
                        <span>·</span>
                        <span>last added {formatDate(rule.lastGeneratedDate)}</span>
                      </>
                    )}
                  </div>
                </div>
                <div className="amount">{money(rule.amount)}</div>
                {canManage && (
                  <div className="actions">
                    <button
                      className="btn ghost"
                      type="button"
                      aria-label={`${rule.isActive ? 'Pause' : 'Resume'} ${rule.categoryName} recurring expense`}
                      onClick={() => void toggleActive(rule)}
                    >
                      <Icon name={rule.isActive ? 'pause' : 'play_arrow'} size={17} />
                    </button>
                    <button
                      className="btn ghost"
                      type="button"
                      aria-label={`Edit ${rule.categoryName} recurring expense`}
                      onClick={() => setEditing(rule)}
                    >
                      <Icon name="edit" size={17} />
                    </button>
                    <button
                      className="btn ghost"
                      type="button"
                      aria-label={`Delete ${rule.categoryName} recurring expense`}
                      onClick={() =>
                        destructive.ask({
                          title: 'Delete this recurring expense?',
                          message: `${money(rule.amount)} · ${rule.categoryName}. Expenses it already added stay. No new ones will be added.`,
                          run: () => deleteRecurring(householdId, rule.id),
                        })
                      }
                    >
                      <Icon name="delete" size={17} />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      {(creating || editing != null) && (
        <RecurringForm
          categories={categories.map((c) => ({ id: c.id, name: c.name }))}
          existing={editing ?? undefined}
          householdId={householdId}
          uid={session.uid}
          displayName={session.displayName}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}

      {destructive.node}
      {errorToast.node}
    </>
  )
}

function RecurringForm({
  categories,
  existing,
  householdId,
  uid,
  displayName,
  onClose,
}: {
  categories: Array<{ id: string; name: string }>
  existing?: RecurringExpense
  householdId: string
  uid: string
  displayName: string
  onClose: () => void
}) {
  const [amount, setAmount] = useState(existing ? String(existing.amount) : '')
  const [categoryId, setCategoryId] = useState(existing?.categoryId ?? categories[0]?.id ?? '')
  const [frequency, setFrequency] = useState<RecurringFrequency>(existing?.frequency ?? 2)
  const [dayOfMonth, setDayOfMonth] = useState(String(existing?.dayOfMonth ?? 1))
  const [dayOfWeek, setDayOfWeek] = useState(String(existing?.dayOfWeek ?? 1))
  const [startDate, setStartDate] = useState(toDateInput(existing?.startDate ?? Date.now()))
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [payment, setPayment] = useState<PaymentWire>(existing?.paymentMethod ?? 'upi')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const parsed = Number(amount)
  const valid = Number.isFinite(parsed) && parsed > 0 && categoryId !== ''

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError('Enter an amount greater than zero and choose a category.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await upsertRecurring({
        id: existing?.id ?? crypto.randomUUID(),
        householdId,
        amount: parsed,
        categoryId,
        categoryName: categories.find((c) => c.id === categoryId)?.name ?? '',
        notes: notes.trim(),
        addedBy: existing?.addedBy ?? uid,
        addedByName: existing?.addedByName ?? displayName,
        frequency,
        // Only the field the chosen frequency actually uses is written; the rest stay null so
        // the shared schedule calculator is not handed a contradictory rule.
        dayOfWeek: frequency === 1 ? Number(dayOfWeek) : null,
        dayOfMonth: frequency === 2 || frequency === 3 ? Number(dayOfMonth) : null,
        monthOfYear: frequency === 3 ? new Date(fromDateInput(startDate)).getMonth() + 1 : null,
        startDate: fromDateInput(startDate),
        endDate: existing?.endDate ?? null,
        lastGeneratedDate: existing?.lastGeneratedDate ?? null,
        isActive: existing?.isActive ?? true,
        paymentMethod: payment,
        createdAt: existing?.createdAt ?? Date.now(),
      })
      onClose()
    } catch (caught) {
      setError(`Couldn't save the recurring expense. ${(caught as Error)?.message ?? 'Check your connection and try again.'}`)
      setBusy(false)
    }
  }

  return (
    <Modal
      title={existing ? 'Edit recurring expense' : 'New recurring expense'}
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="recurring-form" disabled={busy || !valid}>
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
      <form id="recurring-form" onSubmit={submit}>
        <Field label="Amount" hint={parsed > 0 ? money(parsed) : undefined}>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            autoFocus
            required
          />
        </Field>

        <Field label="Category">
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Frequency">
          <Segmented
            options={([0, 1, 2, 3] as RecurringFrequency[]).map((value) => ({
              value: String(value),
              label: FREQUENCY_LABELS[value],
            }))}
            value={String(frequency)}
            onChange={(value) => setFrequency(Number(value) as RecurringFrequency)}
          />
        </Field>

        {frequency === 1 && (
          <Field label="Day of week">
            <select value={dayOfWeek} onChange={(event) => setDayOfWeek(event.target.value)}>
              {['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'].map(
                (label, index) => (
                  <option key={label} value={index + 1}>
                    {label}
                  </option>
                ),
              )}
            </select>
          </Field>
        )}

        {(frequency === 2 || frequency === 3) && (
          <Field
            label="Day of month"
            hint="In shorter months, 31 means the last day of the month."
          >
            <input
              type="number"
              min="1"
              max="31"
              value={dayOfMonth}
              onChange={(event) => setDayOfMonth(event.target.value)}
            />
          </Field>
        )}

        <Field label="Starts on">
          <input
            type="date"
            value={startDate}
            onChange={(event) => setStartDate(event.target.value)}
            required
          />
        </Field>

        <Field label="Payment method">
          <Segmented
            options={paymentMethods().map((method) => ({
              value: method.wire,
              label: `${method.emoji} ${method.label}`,
            }))}
            value={payment}
            onChange={setPayment}
          />
        </Field>

        <Field label="Notes">
          <input
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="For example, Flat rent"
            maxLength={200}
          />
        </Field>
      </form>
    </Modal>
  )
}
