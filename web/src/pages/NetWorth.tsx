import { useCallback, useState } from 'react'

import { PageHead } from '../components/Layout'
import {
  Card,
  CardHeader,
  Empty,
  Field,
  Icon,
  ListSkeleton,
  Modal,
  Notice,
  Stat,
  formatDate,
  useConfirmAction,
} from '../components/ui'
import { useCollection } from '../data/HouseholdData'
import {
  deleteAsset,
  deleteLiability,
  observeAssets,
  observeLiabilities,
  upsertAsset,
  upsertLiability,
} from '../data/networth'
import { useSession } from '../session/SessionProvider'
import { money } from '../shared'
import type { Asset, Liability } from '../types'

const ASSET_TYPES = ['Cash', 'Bank', 'Investment', 'Property', 'Gold', 'Vehicle', 'Other']
const LIABILITY_TYPES = ['Home loan', 'Personal loan', 'Vehicle loan', 'Credit card', 'Other']

export function NetWorth() {
  const session = useSession()
  const householdId = session.household.id
  const canManage = session.allows('manageSharedConfig')

  const subscribeAssets = useCallback(
    (onChange: (rows: Asset[]) => void, onError: (error: Error) => void) =>
      observeAssets(householdId, onChange, onError),
    [householdId],
  )
  const subscribeLiabilities = useCallback(
    (onChange: (rows: Liability[]) => void, onError: (error: Error) => void) =>
      observeLiabilities(householdId, onChange, onError),
    [householdId],
  )

  const assets = useCollection<Asset>(subscribeAssets, [householdId])
  const liabilities = useCollection<Liability>(subscribeLiabilities, [householdId])

  const destructive = useConfirmAction()

  const [editing, setEditing] = useState<
    { kind: 'asset'; row?: Asset } | { kind: 'liability'; row?: Liability } | null
  >(null)

  const totalAssets = assets.rows.reduce((sum, row) => sum + row.value, 0)
  const totalLiabilities = liabilities.rows.reduce((sum, row) => sum + row.amount, 0)
  const net = totalAssets - totalLiabilities

  const error = assets.error ?? liabilities.error

  return (
    <>
      <PageHead title="Net worth" subtitle="What your household owns, minus what it owes">
        {canManage && (
          <>
            <button className="btn" type="button" onClick={() => setEditing({ kind: 'asset' })}>
              <Icon name="add" />
              Add asset
            </button>
            <button className="btn" type="button" onClick={() => setEditing({ kind: 'liability' })}>
              <Icon name="add" />
              Add liability
            </button>
          </>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}

      {!canManage && (
        <div style={{ marginBottom: 16 }}>
          <Notice>Only the household owner and admins can change assets and liabilities.</Notice>
        </div>
      )}

      <div className="grid cols-3" style={{ marginBottom: 20 }}>
        <Stat label="Assets" value={money(totalAssets)} accent="var(--income-text)" />
        <Stat label="Liabilities" value={money(totalLiabilities)} accent="var(--expense-text)" />
        <Stat
          label="Net worth"
          value={money(net)}
          accent={net >= 0 ? 'var(--income-text)' : 'var(--expense-text)'}
        />
      </div>

      {assets.loading || liabilities.loading ? (
        <div className="grid cols-2">
          <Card>
            <CardHeader title="Assets" />
            <ListSkeleton rows={3} />
          </Card>
          <Card>
            <CardHeader title="Liabilities" />
            <ListSkeleton rows={3} />
          </Card>
        </div>
      ) : (
        <div className="grid cols-2">
          <Card>
            <CardHeader title="Assets" />
            {assets.rows.length === 0 ? (
              <Empty icon="account_balance_wallet" title="No assets yet" />
            ) : (
              <div className="list">
                {assets.rows.map((asset) => (
                  <div className="list-row" key={asset.id}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="title">{asset.name}</div>
                      <div className="meta">
                        <span>{asset.type}</span>
                        <span>·</span>
                        <span>{formatDate(asset.date)}</span>
                      </div>
                    </div>
                    <div className="amount" style={{ color: 'var(--income-text)' }}>
                      {money(asset.value)}
                    </div>
                    {canManage && (
                      <div className="actions">
                        <button
                          className="btn ghost"
                          type="button"
                          aria-label={`Edit ${asset.name}`}
                          onClick={() => setEditing({ kind: 'asset', row: asset })}
                        >
                          <Icon name="edit" size={17} />
                        </button>
                        <button
                          className="btn ghost"
                          type="button"
                          aria-label={`Delete ${asset.name}`}
                          onClick={() =>
                            destructive.ask({
                              title: `Delete ${asset.name}?`,
                              message: `Its ${money(asset.value)} will no longer count toward your net worth. This can't be undone.`,
                              run: () => deleteAsset(householdId, asset.id),
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
            )}
          </Card>

          <Card>
            <CardHeader title="Liabilities" />
            {liabilities.rows.length === 0 ? (
              <Empty icon="credit_card_off" title="No liabilities yet" />
            ) : (
              <div className="list">
                {liabilities.rows.map((liability) => (
                  <div className="list-row" key={liability.id}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="title">{liability.name}</div>
                      <div className="meta">
                        <span>{liability.type}</span>
                        <span>·</span>
                        <span>{formatDate(liability.date)}</span>
                      </div>
                    </div>
                    <div className="amount" style={{ color: 'var(--expense-text)' }}>
                      {money(liability.amount)}
                    </div>
                    {canManage && (
                      <div className="actions">
                        <button
                          className="btn ghost"
                          type="button"
                          aria-label={`Edit ${liability.name}`}
                          onClick={() => setEditing({ kind: 'liability', row: liability })}
                        >
                          <Icon name="edit" size={17} />
                        </button>
                        <button
                          className="btn ghost"
                          type="button"
                          aria-label={`Delete ${liability.name}`}
                          onClick={() =>
                            destructive.ask({
                              title: `Delete ${liability.name}?`,
                              message: `Its ${money(liability.amount)} will no longer count against your net worth. This can't be undone.`,
                              run: () => deleteLiability(householdId, liability.id),
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
            )}
          </Card>
        </div>
      )}

      {editing != null && (
        <HoldingForm
          kind={editing.kind}
          householdId={householdId}
          uid={session.uid}
          existing={editing.kind === 'asset' ? editing.row : editing.row}
          onClose={() => setEditing(null)}
        />
      )}

      {destructive.node}
    </>
  )
}

function HoldingForm({
  kind,
  householdId,
  uid,
  existing,
  onClose,
}: {
  kind: 'asset' | 'liability'
  householdId: string
  uid: string
  existing?: Asset | Liability
  onClose: () => void
}) {
  const isAsset = kind === 'asset'
  const types = isAsset ? ASSET_TYPES : LIABILITY_TYPES
  const initialAmount = existing
    ? String((existing as Asset).value ?? (existing as Liability).amount ?? '')
    : ''

  const [name, setName] = useState(existing?.name ?? '')
  const [amount, setAmount] = useState(initialAmount)
  const [type, setType] = useState(existing?.type ?? types[0]!)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const parsed = Number(amount)
  const valid = name.trim() !== '' && Number.isFinite(parsed) && parsed > 0

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError(`Enter a name and ${isAsset ? 'a value' : 'an amount owed'} greater than zero.`)
      return
    }
    setBusy(true)
    setError('')
    try {
      const base = {
        id: existing?.id ?? crypto.randomUUID(),
        householdId,
        name: name.trim(),
        type,
        date: existing?.date ?? Date.now(),
        addedBy: existing?.addedBy ?? uid,
      }
      if (isAsset) await upsertAsset({ ...base, value: parsed })
      else await upsertLiability({ ...base, amount: parsed })
      onClose()
    } catch (caught) {
      setError(`Couldn't save the ${isAsset ? 'asset' : 'liability'}. ${(caught as Error)?.message ?? 'Check your connection and try again.'}`)
      setBusy(false)
    }
  }

  return (
    <Modal
      title={`${existing ? 'Edit' : 'New'} ${isAsset ? 'asset' : 'liability'}`}
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="holding-form" disabled={busy || !valid}>
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
      <form id="holding-form" onSubmit={submit}>
        <Field label="Name">
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={80}
            autoFocus
            required
          />
        </Field>
        <Field label={isAsset ? 'Value' : 'Amount owed'} hint={parsed > 0 ? money(parsed) : undefined}>
          <input
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />
        </Field>
        <Field label="Type">
          <select value={type} onChange={(event) => setType(event.target.value)}>
            {types.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </Field>
      </form>
    </Modal>
  )
}
