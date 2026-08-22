import { useEffect, useState } from 'react'

import { PageHead } from '../components/Layout'
import { Card, Field, Icon, Modal, Notice, Spinner } from '../components/ui'
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
      setMessage((caught as Error)?.message ?? 'That did not work.')
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

  return (
    <>
      <PageHead title={household.name} subtitle={roleDescription(role) || 'Household'}>
        {session.allows('renameHousehold') && (
          <button className="btn" type="button" onClick={() => setRenaming(true)}>
            <Icon name="edit" />
            Rename
          </button>
        )}
      </PageHead>

      {message !== '' && (
        <div style={{ marginBottom: 16 }}>
          <Notice>{message}</Notice>
        </div>
      )}

      <div className="grid cols-2">
        <Card>
          <strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>Members</strong>
          <p style={{ margin: '0 0 14px', fontSize: 12, color: 'var(--text-secondary)' }}>
            {household.memberUids.length} of 20
          </p>

          {members == null ? (
            <Spinner label="Loading members…" />
          ) : (
            <div className="list">
              {members.map((member) => (
                <div className="list-row" key={member.uid}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="title">
                      {member.displayName}
                      {member.uid === session.uid && (
                        <span style={{ color: 'var(--text-tertiary)', fontWeight: 400 }}> · you</span>
                      )}
                    </div>
                    <div className="meta">
                      <span className={`badge ${member.isOwner ? 'owner' : ''}`}>
                        {roleLabel(member.role)}
                      </span>
                      {member.email !== '' && <span>{member.email}</span>}
                    </div>
                  </div>

                  {/* The owner's role is not changeable: there is deliberately no ownership
                      transfer, so it is not offered rather than offered and rejected. */}
                  {session.allows('manageMembers') && !member.isOwner && (
                    <div className="row" style={{ gap: 6 }}>
                      <select
                        value={member.role}
                        disabled={busy}
                        onChange={(event) =>
                          void act(
                            () => setMemberRole(household, member.uid, event.target.value as RoleWire),
                            `${member.displayName} is now a ${event.target.value}.`,
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
                        onClick={() => {
                          if (
                            window.confirm(
                              `Remove ${member.displayName}? Their expenses stay in the household.`,
                            )
                          )
                            void act(
                              () => removeMember(household, member.uid),
                              `${member.displayName} was removed.`,
                            )
                        }}
                      >
                        <Icon name="person_remove" size={18} />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>

        <div className="grid" style={{ gap: 'var(--gap)', alignContent: 'start' }}>
          {session.allows('manageInviteCode') ? (
            <Card>
              <strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>Invite code</strong>
              <p style={{ margin: '0 0 12px', fontSize: 12, color: 'var(--text-secondary)' }}>
                Anyone with this code can join as a member. Rotating it stops the old one working.
              </p>
              <div className="code" style={{ marginBottom: 12 }}>
                {household.inviteCode || '——————'}
              </div>
              <div className="row" style={{ gap: 8 }}>
                <button className="btn" type="button" onClick={() => void copyCode()}>
                  <Icon name={copied ? 'check' : 'content_copy'} />
                  {copied ? 'Copied' : 'Copy'}
                </button>
                <button
                  className="btn"
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    if (window.confirm('Rotate the code? The current one stops working.'))
                      void act(() => rotateInviteCode(household), 'A new code has been issued.')
                  }}
                >
                  <Icon name="autorenew" />
                  Rotate
                </button>
              </div>
            </Card>
          ) : (
            <Card>
              <strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>Invite code</strong>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)' }}>
                Only the household owner can issue or rotate the invite code.
              </p>
            </Card>
          )}

          <Card>
            <strong style={{ fontSize: 15, display: 'block', marginBottom: 4 }}>Your access</strong>
            <p style={{ margin: '0 0 12px', fontSize: 13, color: 'var(--text-secondary)' }}>
              {roleDescription(role)}
            </p>

            {session.allows('leaveHousehold') && (
              <button
                className="btn danger"
                type="button"
                disabled={busy}
                onClick={() => {
                  if (window.confirm('Leave this household? Your expenses stay behind.'))
                    void act(async () => {
                      await leaveHousehold(household, session.uid)
                      await signOut()
                    }, 'You have left the household.')
                }}
              >
                <Icon name="logout" />
                Leave household
              </button>
            )}

            {session.allows('deleteHousehold') && (
              <>
                <div style={{ marginTop: 14 }}>
                  <Notice kind="warn">
                    Deleting removes the household itself, but Firestore does not cascade — the
                    expenses, categories and budgets underneath survive with no way to reach
                    them. Doing this cleanly needs a Cloud Function.
                  </Notice>
                </div>
                <button
                  className="btn danger"
                  type="button"
                  style={{ marginTop: 12 }}
                  onClick={() => setConfirmDelete(true)}
                >
                  <Icon name="delete_forever" />
                  Delete household
                </button>
              </>
            )}
          </Card>
        </div>
      </div>

      {renaming && (
        <RenameForm
          currentName={household.name}
          busy={busy}
          onClose={() => setRenaming(false)}
          onSubmit={(name) =>
            void act(async () => {
              await renameHousehold(household, name)
              setRenaming(false)
            }, 'Household renamed.')
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
            }, 'Household deleted.')
          }
        />
      )}
    </>
  )
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
      <Field label="Name">
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
      title="Delete household"
      subtitle="This cannot be undone, and it does not delete the data underneath."
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
            {busy ? 'Deleting…' : 'Delete permanently'}
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
