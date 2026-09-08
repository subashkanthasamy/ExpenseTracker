import { useState } from 'react'

import { PageHead } from '../components/Layout'
import {
  Card,
  CardHeader,
  CategoryIcon,
  Empty,
  Field,
  Icon,
  Modal,
  Notice,
  Skeleton,
  useConfirmAction,
} from '../components/ui'
import { deleteCategory, topUpPresetCategories, upsertCategory } from '../data/categories'
import { useHouseholdData } from '../data/HouseholdData'
import { useSession } from '../session/SessionProvider'
import { categoryIconKey, categoryPresets, colorOf } from '../shared'
import type { Category } from '../types'

/** ARGB number for a `#rrggbb`, opaque. Categories store the colour the way Android writes it. */
const toArgb = (hex: string): number => 0xff000000 + parseInt(hex.replace('#', ''), 16)

export function Categories() {
  const session = useSession()
  const canManage = session.allows('manageSharedConfig')
  const { categories, loading, error } = useHouseholdData()

  const [editing, setEditing] = useState<Category | null>(null)
  const [creating, setCreating] = useState(false)
  const [toppingUp, setToppingUp] = useState(false)
  const [message, setMessage] = useState('')
  const destructive = useConfirmAction()

  const presets = categories.filter((c) => c.isPreset)
  const custom = categories.filter((c) => !c.isPreset)

  const topUp = async () => {
    setToppingUp(true)
    setMessage('')
    try {
      const added = await topUpPresetCategories(session.household)
      setMessage(
        added === 0
          ? 'Already up to date — nothing was missing.'
          : `Added ${added} categor${added === 1 ? 'y' : 'ies'}.`,
      )
    } catch (caught) {
      setMessage((caught as Error)?.message ?? 'Could not top up.')
    } finally {
      setToppingUp(false)
    }
  }

  const remove = (category: Category) =>
    destructive.ask({
      title: `Delete "${category.name}"?`,
      message:
        'Existing expenses keep the name they were filed under, so nothing disappears from your history — the category just stops being offered.',
      run: () => deleteCategory(session.household.id, category.id),
    })

  return (
    <>
      <PageHead title="Categories" subtitle={`${categories.length} in this household`}>
        {canManage && (
          <>
            <button className="btn" type="button" onClick={() => void topUp()} disabled={toppingUp}>
              <Icon name="refresh" />
              {toppingUp ? 'Checking…' : 'Add missing presets'}
            </button>
            <button className="btn primary" type="button" onClick={() => setCreating(true)}>
              <Icon name="add" />
              New category
            </button>
          </>
        )}
      </PageHead>

      {error != null && (
        <div style={{ marginBottom: 16 }}>
          <Notice kind="error">{error}</Notice>
        </div>
      )}
      {message !== '' && (
        <div style={{ marginBottom: 16 }}>
          <Notice>{message}</Notice>
        </div>
      )}
      {!canManage && (
        <div style={{ marginBottom: 16 }}>
          <Notice>
            Categories are shared configuration, managed by the household owner. Deleting one
            here would change what everyone sees.
          </Notice>
        </div>
      )}

      {loading ? (
        // Placeholder tiles at the real tile size, so the grid does not reflow on arrival.
        <div className="grid cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <Card key={index}>
              <Skeleton height={38} width={38} radius={11} />
              <div style={{ marginTop: 12 }}>
                <Skeleton height={13} width="70%" />
              </div>
            </Card>
          ))}
        </div>
      ) : categories.length === 0 ? (
        <Card>
          <Empty
            icon="category"
            title="No categories yet"
            action={
              canManage ? (
                <button className="btn primary" type="button" onClick={() => void topUp()}>
                  Seed the {categoryPresets().length} presets
                </button>
              ) : undefined
            }
          >
            The household owner needs to open the app once to seed the preset categories.
          </Empty>
        </Card>
      ) : (
        <>
          {custom.length > 0 && (
            <Card style={{ marginBottom: 20 }}>
              <CardHeader title="Your own" />
              <CategoryGrid
                categories={custom}
                canManage={canManage}
                onEdit={setEditing}
                onDelete={remove}
              />
            </Card>
          )}

          <Card>
            <CardHeader
              title="Presets"
              sub="Seeded from the shared catalogue, so Android, iOS and the web agree. Deleting one is remembered — it will not come back on the next load."
            />
            <CategoryGrid
              categories={presets}
              canManage={canManage}
              onEdit={setEditing}
              onDelete={remove}
            />
          </Card>
        </>
      )}

      {(creating || editing != null) && (
        <CategoryForm
          householdId={session.household.id}
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

function CategoryGrid({
  categories,
  canManage,
  onEdit,
  onDelete,
}: {
  categories: Category[]
  canManage: boolean
  onEdit: (category: Category) => void
  onDelete: (category: Category) => void
}) {
  return (
    <div className="grid cols-4">
      {categories.map((category) => (
        <div
          key={category.id}
          className="row"
          style={{
            gap: 10,
            padding: 10,
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          <CategoryIcon name={category.name} icon={category.icon} color={colorOf(category)} size={34} />
          <span style={{ fontSize: 13, fontWeight: 500, flex: 1, minWidth: 0 }}>{category.name}</span>
          {canManage && (
            <div className="row" style={{ gap: 2 }}>
              <button className="btn ghost icon" type="button" onClick={() => onEdit(category)}>
                <Icon name="edit" size={16} />
              </button>
              <button className="btn ghost icon" type="button" onClick={() => onDelete(category)}>
                <Icon name="delete" size={16} />
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function CategoryForm({
  householdId,
  existing,
  onClose,
}: {
  householdId: string
  existing?: Category
  onClose: () => void
}) {
  const [name, setName] = useState(existing?.name ?? '')
  const [color, setColor] = useState(
    existing ? colorOf(existing) : '#7b61ff',
  )
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const valid = name.trim() !== ''
  // Shown live, so the icon that will actually appear is visible before saving. The key is
  // derived from the name by the shared resolver, exactly as the mobile clients do it.
  const previewIcon = categoryIconKey(existing?.icon ?? null, name || 'Misc')

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!valid) {
      setError('Give the category a name.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await upsertCategory(householdId, {
        id: existing?.id ?? crypto.randomUUID(),
        name: name.trim(),
        // Store the resolved canonical key rather than a free-form string, so every client
        // renders the same glyph.
        icon: previewIcon,
        color: toArgb(color),
        isPreset: existing?.isPreset ?? false,
      })
      onClose()
    } catch (caught) {
      setError((caught as Error)?.message ?? 'Could not save.')
      setBusy(false)
    }
  }

  return (
    <Modal
      title={existing ? 'Edit category' : 'New category'}
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn primary" type="submit" form="category-form" disabled={busy || !valid}>
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
      <form id="category-form" onSubmit={submit}>
        <div className="row" style={{ gap: 12, marginBottom: 16 }}>
          <CategoryIcon name={name || 'Misc'} icon={previewIcon} color={color} size={46} />
          <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            Icon is chosen from the name — “{previewIcon}”
          </div>
        </div>

        <Field label="Name" hint="Names like Milk, Fuel or Electricity get a matching icon automatically.">
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={40}
            autoFocus
            required
          />
        </Field>

        <Field label="Colour">
          <input
            type="color"
            value={color}
            onChange={(event) => setColor(event.target.value)}
            style={{ height: 42, padding: 4 }}
          />
        </Field>
      </form>
    </Modal>
  )
}
