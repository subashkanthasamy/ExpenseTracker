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
          ? 'All preset categories are already here'
          : `${added} ${added === 1 ? 'category' : 'categories'} added`,
      )
    } catch (caught) {
      setMessage(`Couldn't add the missing presets. ${(caught as Error)?.message ?? 'Try again.'}`)
    } finally {
      setToppingUp(false)
    }
  }

  const remove = (category: Category) =>
    destructive.ask({
      title: `Delete "${category.name}"?`,
      message:
        'Past expenses keep this category name, so your history isn\'t affected. It just won\'t be offered for new expenses.',
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
            Only the household owner and admins can change categories, because changes affect
            everyone in the household.
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
                  Add {categoryPresets().length} preset categories
                </button>
              ) : undefined
            }
          >
            {canManage
              ? 'Add the preset categories to get started.'
              : "They'll appear after the household owner opens the app."}
          </Empty>
        </Card>
      ) : (
        <>
          {custom.length > 0 && (
            <Card style={{ marginBottom: 20 }}>
              <CardHeader title="Custom" />
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
              sub="Built-in categories, the same on Android, iOS and the web. A preset you delete won't come back on its own."
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
    <div className="category-grid">
      {categories.map((category) => (
        <div key={category.id} className="category-tile">
          <CategoryIcon name={category.name} icon={category.icon} color={colorOf(category)} size={34} />
          <span className="name" title={category.name}>
            {category.name}
          </span>
          {canManage && (
            <div className="row" style={{ gap: 2 }}>
              <button
                className="btn ghost icon"
                type="button"
                aria-label={`Edit ${category.name}`}
                onClick={() => onEdit(category)}
              >
                <Icon name="edit" size={16} />
              </button>
              <button
                className="btn ghost icon"
                type="button"
                aria-label={`Delete ${category.name}`}
                onClick={() => onDelete(category)}
              >
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
      setError('Enter a category name.')
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
      setError(`Couldn't save the category. ${(caught as Error)?.message ?? 'Check your connection and try again.'}`)
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
            The icon is picked from the name.
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
