import { useState } from 'react'

import { createExpense, updateExpense } from '../data/expenses'
import { useSession } from '../session/SessionProvider'
import { expenseScopes, money, paymentMethods, parseSms } from '../shared'
import type { Category, Expense, PaymentWire, ScopeWire } from '../types'
import { Field, Icon, Modal, Notice, Segmented, fromDateInput, toDateInput } from './ui'

/**
 * Add or edit an expense.
 *
 * Two fields matter beyond the obvious:
 *
 * - **Scope** decides who can see the row. Personal rows are visible to their author and to
 *   the owner and admins, and are excluded from every shared total — the point is the surprise
 *   gift, where the person you are hiding a purchase from is in the same household.
 * - **Payment method** is only ever recorded, never inferred. An expense written before the
 *   field existed stays Unspecified rather than being defaulted to Cash, which would invent
 *   data and make the breakdown lie.
 */
export function ExpenseForm({
  categories,
  existing,
  onClose,
}: {
  categories: Category[]
  existing?: Expense
  onClose: () => void
}) {
  const session = useSession()
  const isEditing = existing != null

  const [amount, setAmount] = useState(existing ? String(existing.amount) : '')
  const [categoryId, setCategoryId] = useState(existing?.categoryId ?? categories[0]?.id ?? '')
  const [date, setDate] = useState(toDateInput(existing?.date ?? Date.now()))
  const [notes, setNotes] = useState(existing?.notes ?? '')
  const [payment, setPayment] = useState<PaymentWire>(existing?.paymentMethod ?? 'upi')
  const [scope, setScope] = useState<ScopeWire>(existing?.scope ?? 'shared')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [pasteOpen, setPasteOpen] = useState(false)
  const [pasted, setPasted] = useState('')

  const parsedAmount = Number(amount)
  const valid = Number.isFinite(parsedAmount) && parsedAmount > 0 && categoryId !== ''

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError('Enter an amount above zero and pick a category.')
      return
    }
    const category = categories.find((c) => c.id === categoryId)
    setBusy(true)
    setError('')
    const now = Date.now()

    try {
      if (isEditing) {
        await updateExpense(session.household.id, {
          ...existing,
          amount: parsedAmount,
          categoryId,
          // Denormalised onto the row, as on the mobile clients, so history keeps the name it
          // was filed under even if the category is later renamed.
          categoryName: category?.name ?? existing.categoryName,
          date: fromDateInput(date),
          notes: notes.trim(),
          paymentMethod: payment,
          scope,
          updatedAt: now,
        })
      } else {
        await createExpense(session.household.id, {
          amount: parsedAmount,
          categoryId,
          categoryName: category?.name ?? '',
          date: fromDateInput(date),
          notes: notes.trim(),
          // Must be the caller: the rules reject a row filed against someone else, which
          // would also poison the per-member spending split.
          addedBy: session.uid,
          addedByName: session.displayName,
          createdAt: now,
          updatedAt: now,
          paymentMethod: payment,
          scope,
        })
      }
      onClose()
    } catch (caught) {
      setError((caught as Error)?.message ?? 'Could not save.')
      setBusy(false)
    }
  }

  /** Fills the form from a pasted bank alert using the shared SMS parser. */
  const applyPaste = () => {
    const parsed = parseSms('BANK', pasted)
    if (!parsed) {
      setError('That does not look like a transaction alert.')
      return
    }
    setAmount(String(parsed.amount))
    if (parsed.paymentMethod !== '') setPayment(parsed.paymentMethod)
    if (parsed.categoryName != null) {
      const match = categories.find(
        (c) => c.name.trim().toLowerCase() === parsed.categoryName!.trim().toLowerCase(),
      )
      if (match) setCategoryId(match.id)
    }
    if (parsed.merchant != null && notes.trim() === '') setNotes(parsed.merchant)
    setPasteOpen(false)
    setPasted('')
    setError('')
  }

  return (
    <Modal
      title={isEditing ? 'Edit expense' : 'Add expense'}
      subtitle={
        scope === 'personal'
          ? 'Personal: visible to you and the household owner, and left out of shared totals.'
          : 'Shared: visible to everyone in the household and counted in shared totals.'
      }
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="expense-form" disabled={busy || !valid}>
            {busy ? 'Saving…' : isEditing ? 'Save changes' : 'Add expense'}
          </button>
        </>
      }
    >
      {error !== '' && (
        <div style={{ marginBottom: 14 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      <form id="expense-form" onSubmit={submit}>
        <Field label="Amount" hint={parsedAmount > 0 ? money(parsedAmount) : undefined}>
          <input
            type="number"
            inputMode="decimal"
            step="0.01"
            min="0"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            autoFocus
            required
          />
        </Field>

        <Field label="Category">
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
            {categories.length === 0 && <option value="">No categories yet</option>}
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Date">
          <input type="date" value={date} onChange={(event) => setDate(event.target.value)} required />
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

        <Field label="Visibility">
          <Segmented
            options={expenseScopes().map((option) => ({ value: option.wire, label: option.label }))}
            value={scope}
            onChange={setScope}
          />
        </Field>

        <Field label="Notes">
          <input
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            placeholder="Optional"
            maxLength={200}
          />
        </Field>
      </form>

      {!isEditing && (
        <>
          {pasteOpen ? (
            <div style={{ marginTop: 4 }}>
              <Field
                label="Paste a bank alert"
                hint="The browser cannot read your SMS inbox, so pasting is the web equivalent of Android's automatic import. The parser is the same one."
              >
                <textarea
                  rows={3}
                  value={pasted}
                  onChange={(event) => setPasted(event.target.value)}
                  placeholder="Rs 450.00 debited from a/c XX1234 to SWIGGY via UPI…"
                />
              </Field>
              <div className="row" style={{ gap: 8 }}>
                <button className="btn" type="button" onClick={applyPaste} disabled={pasted.trim() === ''}>
                  <Icon name="auto_fix_high" />
                  Fill from message
                </button>
                <button className="btn ghost" type="button" onClick={() => setPasteOpen(false)}>
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <button
              className="btn ghost"
              type="button"
              style={{ paddingLeft: 0 }}
              onClick={() => setPasteOpen(true)}
            >
              <Icon name="content_paste" />
              Fill from a bank message
            </button>
          )}
        </>
      )}

      {categories.length === 0 && (
        <div style={{ marginTop: 12 }}>
          <Notice kind="warn">
            This household has no categories yet. The owner needs to open the app once to seed them.
          </Notice>
        </div>
      )}
    </Modal>
  )
}
