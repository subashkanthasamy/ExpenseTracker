import { useEffect, useState } from 'react'

import {
  Card,
  CardHeader,
  Field,
  formatDate,
  Icon,
  ListSkeleton,
  Modal,
  Notice,
  useConfirmAction,
} from '../components/ui'
import {
  deleteHousehold,
  ensureInviteCodePublished,
  getMembers,
  leaveHousehold,
  removeMember,
  renameHousehold,
  rotateInviteCode,
  setMemberRole,
  type Member,
} from '../data/household'
import { signOut } from '../session/auth'
import { useSession } from '../session/SessionProvider'
import { roleDescription, roleLabel } from '../shared'
import type { RoleWire } from '../types'

export function HouseholdPage() {
  const session = useSession()
  const { household, role } = session

  const [members, setMembers] = useState<Member[] | null>(null)
  const [renaming, setRenaming] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [copied, setCopied] = useState(false)
  // `act` reports both success and failure through the page's own Notice, so the hook's toast
  // never fires here — it is used purely for the confirmation step.
  const destructive = useConfirmAction()

  useEffect(() => {
    let cancelled = false
    void getMembers(household).then((rows) => {
      if (!cancelled) setMembers(rows)
    })
    return () => {
      cancelled = true
    }
  }, [household])

  // Households created before the invite-code lookup existed have no `inviteCodes/{code}`
  // document, so they cannot be joined. This is the natural place to heal it: the only screen
  // that shows the code is the one you open when you are about to share it.
  useEffect(() => {
    if (session.allows('manageInviteCode')) void ensureInviteCodePublished(household)
  }, [household, session])

  const act = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true)
    setMessage('')
    try {
      await action()
      setMessage(success)
      setMembers(await getMembers(household))
    } catch (caught) {
      setMessage(`Couldn't make that change. ${(caught as Error)?.message ?? 'Try again.'}`)
    } finally {
      setBusy(false)
    }
  }

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(household.inviteCode)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      /* Clipboard is unavailable over plain HTTP; the code is on screen to read. */
    }
  }

  const canManage = session.allows('manageMembers')
  const canLeave = session.allows('leaveHousehold')
  const canDelete = session.allows('deleteHousehold')

  return (
    <>
      {/* The household is the subject of the page, so it gets a header card of its own rather
          than a bare page title: who it is, how big it is, and where you stand in it. */}
      <section className="card household-hero" aria-label="Household">
        <span className="household-avatar" aria-hidden="true">
          {initials(household.name)}
        </span>
        <div className="household-hero-text">
          <h1>{household.name}</h1>
          <div className="household-facts">
            <span className="badge owner">{roleLabel(role)}</span>
            <span>
              {household.memberUids.length} {household.memberUids.length === 1 ? 'member' : 'members'}
            </span>
            {household.createdAt > 0 && <span>Created {formatDate(household.createdAt)}</span>}
          </div>
        </div>
        {session.allows('renameHousehold') && (
          <button className="btn" type="button" onClick={() => setRenaming(true)}>
            <Icon name="edit" />
            Rename
          </button>
        )}
      </section>

      {message !== '' && (
        <div style={{ marginBottom: 16 }}>
          <Notice>{message}</Notice>
        </div>
      )}

      <div className="household-grid">
        <Card>
          <CardHeader title="Members" sub={`${household.memberUids.length} of 20 places used`} />

          {members == null ? (
            <ListSkeleton rows={3} />
          ) : (
            <div className="list">
              {members.map((member) => {
                // The owner's role is not changeable: there is deliberately no ownership
                // transfer, so it is not offered rather than offered and rejected.
                const editable = canManage && !member.isOwner
                return (
                  <div className="list-row member-row" key={member.uid}>
                    <span className="member-avatar" style={avatarTint(member.uid)} aria-hidden="true">
                      {initials(member.displayName)}
                    </span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div className="title">
                        {member.displayName}
                        {member.uid === session.uid && <span className="you"> · you</span>}
                      </div>
                      <div className="meta">
                        {/* With a role picker beside it, a badge would say the same thing twice. */}
                        {!editable && (
                          <span className={`badge ${member.isOwner ? 'owner' : ''}`}>{roleLabel(member.role)}</span>
                        )}
                        {member.email !== '' && <span className="ellipsis">{member.email}</span>}
                      </div>
                    </div>

                    {editable && (
                      <div className="row" style={{ gap: 4 }}>
                        <select
                          aria-label={`Role for ${member.displayName}`}
                          value={member.role}
                          disabled={busy}
                          onChange={(event) =>
                            void act(
                              () => setMemberRole(household, member.uid, event.target.value as RoleWire),
                              `${member.displayName}'s role changed to ${roleLabel(event.target.value as RoleWire)}`,
                            )
                          }
                        >
                          <option value="admin">Admin</option>
                          <option value="member">Member</option>
                          <option value="guest">Guest</option>
                        </select>
                        <button
                          className="btn ghost icon"
                          type="button"
                          disabled={busy}
                          aria-label={`Remove ${member.displayName} from the household`}
                          title="Remove from household"
                          onClick={() =>
                            destructive.ask({
                              title: `Remove ${member.displayName}?`,
                              message:
                                'Their expenses stay in the household with their name on them. You can invite them back with an invite code.',
                              confirmLabel: 'Remove from household',
                              run: () =>
                                act(
                                  () => removeMember(household, member.uid),
                                  `${member.displayName} removed from household`,
                                ),
                            })
                          }
                        >
                          <Icon name="person_remove" size={20} />
                        </button>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </Card>

        <div className="household-side">
          <Card>
            <CardHeader
              title="Invite code"
              sub={
                session.allows('manageInviteCode')
                  ? 'Anyone with this code can join as a member.'
                  : 'Only the household owner can create or change the invite code.'
              }
            />
            {session.allows('manageInviteCode') && (
              <>
                <div className="invite-code">
                  <span className="code-value">{household.inviteCode || '——————'}</span>
                  <button
                    className="btn ghost icon"
                    type="button"
                    aria-label={copied ? 'Invite code copied' : 'Copy invite code'}
                    title={copied ? 'Invite code copied' : 'Copy invite code'}
                    onClick={() => void copyCode()}
                  >
                    <Icon name={copied ? 'check' : 'content_copy'} size={20} />
                  </button>
                </div>
                <div className="invite-foot">
                  <span className="t-xs t-secondary">Getting a new code stops the old one from working.</span>
                  <button
                    className="btn ghost"
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      destructive.ask({
                        title: 'Get a new invite code?',
                        message:
                          'The current code stops working right away, even if someone has already shared it. People already in the household aren\'t affected.',
                        confirmLabel: 'Get new code',
                        run: () =>
                          act(() => rotateInviteCode(household), 'New invite code created'),
                      })
                    }
                  >
                    <Icon name="autorenew" />
                    New code
                  </button>
                </div>
              </>
            )}
          </Card>

          <Card>
            <CardHeader title="Your access" />
            <div className="access-row">
              <span className="access-icon" aria-hidden="true">
                <Icon name={role === 'owner' || role === 'admin' ? 'shield_person' : 'person'} />
              </span>
              <div>
                <div className="t-strong">{roleLabel(role)}</div>
                <div className="t-sm t-secondary">{roleDescription(role)}</div>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {(canLeave || canDelete) && (
        <section className="card danger-zone" aria-labelledby="danger-zone-title">
          <h2 id="danger-zone-title">Danger zone</h2>
          {canLeave && (
            <div className="danger-row">
              <div>
                <div className="t-strong">Leave household</div>
                <div className="t-sm t-secondary">
                  Your expenses stay in the household with your name on them, and you'll be signed out.
                </div>
              </div>
              <button
                className="btn danger"
                type="button"
                disabled={busy}
                onClick={() =>
                  destructive.ask({
                    title: 'Leave this household?',
                    message:
                      'Your expenses stay in the household with your name on them, and you\'ll be signed out. To rejoin, you\'ll need an invite code.',
                    confirmLabel: 'Leave household',
                    run: () =>
                      act(async () => {
                        await leaveHousehold(household, session.uid)
                        await signOut()
                      }, 'You left the household'),
                  })
                }
              >
                <Icon name="logout" />
                Leave household
              </button>
            </div>
          )}
          {canDelete && (
            <div className="danger-row">
              <div>
                <div className="t-strong">Delete household</div>
                <div className="t-sm t-secondary">
                  This deletes the household and everything in it — expenses, categories,
                  budgets, savings goals, recurring expenses, assets and liabilities — for every
                  member. This can't be undone.
                </div>
              </div>
              <button className="btn danger" type="button" onClick={() => setConfirmDelete(true)}>
                <Icon name="delete_forever" />
                Delete household
              </button>
            </div>
          )}
        </section>
      )}

      {renaming && (
        <RenameForm
          currentName={household.name}
          busy={busy}
          onClose={() => setRenaming(false)}
          onSubmit={(name) =>
            void act(async () => {
              await renameHousehold(household, name)
              setRenaming(false)
            }, 'Household renamed')
          }
        />
      )}

      {confirmDelete && (
        <DeleteConfirm
          expected={household.name}
          busy={busy}
          onClose={() => setConfirmDelete(false)}
          onConfirm={() =>
            void act(async () => {
              await deleteHousehold(household.id, household.inviteCode)
              setConfirmDelete(false)
            }, 'Household deleted')
          }
        />
      )}

      {destructive.node}
    </>
  )
}

/** Up to two initials, for an avatar. */
function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  const first = words[0]![0] ?? ''
  const last = words.length > 1 ? (words[words.length - 1]![0] ?? '') : ''
  return (first + last).toUpperCase()
}

/** Brand hues for member avatars. Tints of these, never text colours, so any of them works. */
const AVATAR_HUES = ['#7b61ff', '#e040fb', '#ff8a65', '#4caf50', '#29b6f6', '#ffa726']

/**
 * A stable per-member tint, derived from the uid so a person keeps their colour across
 * reloads and devices. Mixed into the card surface, so the text on it stays the ordinary
 * text colour and readable in both appearances.
 */
function avatarTint(uid: string): React.CSSProperties {
  let hash = 0
  for (let i = 0; i < uid.length; i++) hash = (hash * 31 + uid.charCodeAt(i)) | 0
  const hue = AVATAR_HUES[Math.abs(hash) % AVATAR_HUES.length]!
  return { background: `color-mix(in srgb, ${hue} 26%, var(--card))` }
}

function RenameForm({
  currentName,
  busy,
  onClose,
  onSubmit,
}: {
  currentName: string
  busy: boolean
  onClose: () => void
  onSubmit: (name: string) => void
}) {
  const [name, setName] = useState(currentName)
  return (
    <Modal
      title="Rename household"
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            className="btn primary"
            type="button"
            disabled={busy || name.trim() === ''}
            onClick={() => onSubmit(name)}
          >
            {busy ? 'Saving…' : 'Save'}
          </button>
        </>
      }
    >
      <Field label="Household name">
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={100}
          autoFocus
        />
      </Field>
    </Modal>
  )
}

function DeleteConfirm({
  expected,
  busy,
  onClose,
  onConfirm,
}: {
  expected: string
  busy: boolean
  onClose: () => void
  onConfirm: () => void
}) {
  const [typed, setTyped] = useState('')
  return (
    <Modal
      title="Delete this household?"
      subtitle="This deletes everything in the household for every member. This can't be undone."
      onClose={onClose}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button
            className="btn danger"
            type="button"
            disabled={busy || typed !== expected}
            onClick={onConfirm}
          >
            {busy ? 'Deleting…' : 'Delete household'}
          </button>
        </>
      }
    >
      <Field label={`Type "${expected}" to confirm`}>
        <input value={typed} onChange={(event) => setTyped(event.target.value)} autoFocus />
      </Field>
    </Modal>
  )
}
