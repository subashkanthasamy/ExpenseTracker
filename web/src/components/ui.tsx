/** Shared presentational primitives. */
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'

import { categoryIconKey } from '../shared'

/**
 * A Material Symbols glyph.
 *
 * The font renders the name as a ligature, which is why the shared canonical icon key can be
 * passed straight through — the web client needs no key-to-glyph mapping table, unlike iOS
 * where the key has to be translated to an SF Symbol name.
 */
export function Icon({ name, size, color }: { name: string; size?: number; color?: string }) {
  return (
    <span
      className="material-symbols-rounded"
      style={{ fontSize: size, color }}
      aria-hidden="true"
    >
      {name}
    </span>
  )
}

/** Category glyph on its tinted tile, matching the mobile category tiles. */
export function CategoryIcon({
  name,
  icon,
  color,
  size = 38,
}: {
  name: string
  icon?: string | null
  color?: string
  size?: number
}) {
  const tint = color ?? 'var(--accent)'
  return (
    <span
      className="icon-tile"
      style={{
        width: size,
        height: size,
        flexBasis: size,
        background: `color-mix(in srgb, ${tint} 16%, transparent)`,
      }}
    >
      <Icon name={categoryIconKey(icon, name)} size={size * 0.55} color={tint} />
    </span>
  )
}

export function Card({
  children,
  className = '',
  style,
}: {
  children: ReactNode
  className?: string
  style?: React.CSSProperties
}) {
  return (
    <div className={`card ${className}`} style={style}>
      {children}
    </div>
  )
}

export function Stat({
  label,
  value,
  sub,
  accent,
  trend,
}: {
  label: string
  value: string
  sub?: ReactNode
  accent?: string
  /** Optional sparkline, rendered under the value. See `Sparkline` in `charts.tsx`. */
  trend?: ReactNode
}) {
  return (
    <Card>
      <div className="stat-label">{label}</div>
      {/* No tabular-nums here on purpose: equal-width digits make a large standalone
          number read loose. It belongs on columns that align vertically, not on this. */}
      <div className="stat-value" style={{ color: accent }}>
        {value}
      </div>
      {sub != null && <div className="stat-sub">{sub}</div>}
      {trend != null && <div style={{ marginTop: 'var(--space-3)' }}>{trend}</div>}
    </Card>
  )
}

/**
 * A card's heading row, with an optional action on the right.
 *
 * `<strong style={{ fontSize: 15 }}>` was the card heading at nine sites, and Dashboard and
 * Insights both hand-rolled the "heading plus right-hand link" row around it.
 */
export function CardHeader({
  title,
  sub,
  action,
}: {
  title: string
  sub?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="card-header">
      <div>
        <h2>{title}</h2>
        {sub != null && <p className="sub">{sub}</p>}
      </div>
      {action != null && action}
    </div>
  )
}

/* ------------------------------------------------------------------ loading */

/**
 * A shimmering placeholder block.
 *
 * Only for a FIRST load. On a refetch the previous render is held at reduced opacity instead
 * (`Refetching` in `charts.tsx`) — swapping rendered content back to a skeleton is a flash and
 * a layout jump, and showing slightly stale numbers for a moment is the better trade.
 */
export function Skeleton({
  width = '100%',
  height = 14,
  radius,
}: {
  width?: number | string
  height?: number | string
  radius?: number
}) {
  return (
    <div
      className="skeleton"
      style={{ width, height, borderRadius: radius }}
      aria-hidden="true"
    />
  )
}

/** A row of stat tiles at their final size, so the first paint does not then jump. */
export function StatSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className={`grid cols-${count}`} style={{ marginBottom: 'var(--space-5)' }}>
      {Array.from({ length: count }, (_, index) => (
        <Card key={index}>
          <Skeleton width={92} height={11} />
          <div style={{ marginTop: 10 }}>
            <Skeleton width={128} height={26} />
          </div>
          <div style={{ marginTop: 10 }}>
            <Skeleton width={72} height={10} />
          </div>
        </Card>
      ))}
    </div>
  )
}

export function ListSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="list" aria-hidden="true">
      {Array.from({ length: rows }, (_, index) => (
        <div className="list-row" key={index}>
          <Skeleton width={38} height={38} radius={11} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <Skeleton width={`${52 + ((index * 13) % 30)}%`} height={13} />
            <div style={{ marginTop: 7 }}>
              <Skeleton width={112} height={10} />
            </div>
          </div>
          <Skeleton width={64} height={13} />
        </div>
      ))}
    </div>
  )
}

/** Placeholder rows inside the table's own frame, so the header and borders do not move. */
export function TableSkeleton({ rows = 8 }: { rows?: number }) {
  return (
    <div className="panel" aria-hidden="true">
      <div style={{ padding: '0 14px' }}>
        {Array.from({ length: rows }, (_, index) => (
          <div
            key={index}
            className="row"
            style={{ gap: 'var(--space-4)', padding: '13px 0', borderBottom: '1px solid var(--border)' }}
          >
            <Skeleton width={78} height={12} />
            <Skeleton width={26} height={26} radius={8} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <Skeleton width={`${38 + ((index * 17) % 34)}%`} height={12} />
            </div>
            <Skeleton width={72} height={12} />
          </div>
        ))}
      </div>
    </div>
  )
}

export function ChartSkeleton({ height = 168 }: { height?: number }) {
  return <Skeleton height={height} radius={12} />
}

/* ------------------------------------------------------------------ dialogs */

/**
 * Destructive confirmation.
 *
 * Replaces `window.confirm`, which ignores the app's appearance entirely, cannot say which
 * item is about to go, and on some browsers is suppressed outright after a few uses.
 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Delete',
  busy = false,
  onConfirm,
  onCancel,
}: {
  title: string
  message: ReactNode
  confirmLabel?: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Modal
      title={title}
      onClose={onCancel}
      actions={
        <>
          <button className="btn ghost" type="button" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button className="btn danger" type="button" onClick={onConfirm} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </>
      }
    >
      <p style={{ margin: 0, fontSize: 'var(--text-md)', color: 'var(--text-secondary)' }}>{message}</p>
    </Modal>
  )
}

interface DestructiveRequest {
  title: string
  message: ReactNode
  confirmLabel?: string
  /** The work to do once confirmed. A rejection becomes a toast rather than being swallowed. */
  run: () => Promise<void>
}

/**
 * Confirm-then-do, with the failure path handled.
 *
 * Eleven `window.confirm`s and five `window.alert`s were spread across six pages, each with
 * its own copy of the same try/catch. This owns the dialog, the busy flag and the error toast
 * so a call site is one `ask({ … })` plus rendering `node` once:
 *
 * ```tsx
 * const destructive = useConfirmAction()
 * // …
 * onClick={() => destructive.ask({
 *   title: 'Remove this budget?',
 *   message: `${budget.categoryName} will stop being tracked.`,
 *   run: () => deleteBudget(householdId, budget.id),
 * })}
 * // …
 * {destructive.node}
 * ```
 */
export function useConfirmAction(): { ask: (request: DestructiveRequest) => void; node: ReactNode } {
  const [pending, setPending] = useState<DestructiveRequest | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  // Stable, so passing it into a memo or an effect does not invalidate on every render.
  const ask = useCallback((request: DestructiveRequest) => setPending(request), [])

  const run = async (request: DestructiveRequest) => {
    setBusy(true)
    try {
      await request.run()
      setPending(null)
    } catch (caught) {
      // Close the dialog either way: leaving it open beside an error reads as "still working".
      setPending(null)
      setMessage(`That didn't work. ${(caught as Error)?.message ?? 'Try again.'}`)
    } finally {
      setBusy(false)
    }
  }

  const node = (
    <>
      {pending != null && (
        <ConfirmDialog
          title={pending.title}
          message={pending.message}
          confirmLabel={pending.confirmLabel}
          busy={busy}
          onConfirm={() => void run(pending)}
          onCancel={() => setPending(null)}
        />
      )}
      {message !== '' && <Toast message={message} onDismiss={() => setMessage('')} />}
    </>
  )

  return { ask, node }
}

/** Reports a failure that had no confirmation step — the other half of `window.alert`. */
export function useErrorToast(): { show: (error: unknown) => void; node: ReactNode } {
  const [message, setMessage] = useState('')
  const show = useCallback((error: unknown) => {
    setMessage(`Something went wrong. ${(error as Error)?.message ?? 'Try again.'}`)
  }, [])
  const node = message !== '' ? <Toast message={message} onDismiss={() => setMessage('')} /> : null
  return { show, node }
}

/**
 * A transient message, for the paths that used `window.alert`.
 *
 * `role="status"` rather than `alert`: it is announced without stealing focus, which matters
 * because the action that triggered it (a failed delete) has usually left focus somewhere the
 * user still wants it.
 */
export function Toast({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  useEffect(() => {
    const timer = window.setTimeout(onDismiss, 5000)
    return () => window.clearTimeout(timer)
  }, [onDismiss, message])

  return (
    <div className="toast" role="status">
      <Icon name="error" size={18} />
      <span>{message}</span>
      <button className="btn ghost icon" type="button" onClick={onDismiss} aria-label="Dismiss message">
        <Icon name="close" size={17} />
      </button>
    </div>
  )
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: ReactNode
  children: ReactNode
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint != null && <div className="field-hint">{hint}</div>}
    </label>
  )
}

/** Single-choice control. Options are `{ value, label }`; `value` may be null for "any". */
export function Segmented<T extends string | null>({
  options,
  value,
  onChange,
}: {
  options: Array<{ value: T; label: string }>
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="segmented">
      {options.map((option) => (
        <button
          key={String(option.value)}
          type="button"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function Progress({
  value,
  status,
}: {
  /** 0..1; clamped, so an over-budget bar reads as full rather than overflowing. */
  value: number
  status?: 'ok' | 'warning' | 'exceeded'
}) {
  const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0))
  return (
    <div className={`progress ${status ?? ''}`}>
      <div style={{ width: `${clamped * 100}%` }} />
    </div>
  )
}

export function Empty({
  icon,
  title,
  children,
  action,
}: {
  icon: string
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="empty">
      <Icon name={icon} />
      <h3>{title}</h3>
      {children != null && <p>{children}</p>}
      {action != null && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  )
}

export function Notice({
  kind = 'info',
  children,
}: {
  kind?: 'info' | 'error' | 'warn'
  children: ReactNode
}) {
  return <div className={`notice ${kind}`} role={kind === 'error' ? 'alert' : undefined}>{children}</div>
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="row" style={{ gap: 10, padding: 24, color: 'var(--text-secondary)' }}>
      <div className="spinner" />
      {label != null && <span style={{ fontSize: 13 }}>{label}</span>}
    </div>
  )
}

/* ------------------------------------------------------- overlay behaviour */

/*
 * `Modal` and the navigation drawer are different *markup* — a centred box with a heading and
 * a bordered footer, versus a full-height panel of nav links — but identical *behaviour*. So
 * the shared part is extracted as hooks rather than by making one component serve both.
 */

/**
 * Escape dismisses the overlay.
 *
 * Mount-scoped rather than taking an `active` flag: every overlay in this app is rendered
 * conditionally, so "mounted" already means "open".
 */
export function useEscapeToClose(onClose: () => void): void {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
}

let scrollLocks = 0
let scrollRestore = ''

/**
 * Freezes the page behind an overlay.
 *
 * Counted, so a modal opened from inside another overlay cannot unlock the page when only the
 * inner one closes. Deliberately depends on nothing, or the lock would thrash on every render
 * of a parent that passes an inline `onClose`.
 *
 * `html { scrollbar-gutter: stable }` in index.css is what stops this shifting the layout
 * sideways by the scrollbar's width.
 */
export function useScrollLock(): void {
  useEffect(() => {
    if (scrollLocks === 0) {
      scrollRestore = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    }
    scrollLocks += 1
    return () => {
      scrollLocks -= 1
      if (scrollLocks === 0) document.body.style.overflow = scrollRestore
    }
  }, [])
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]),' +
  ' textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Keeps Tab inside `ref`, and hands focus back to whatever opened the overlay.
 *
 * Deliberately not a full trap: it does not watch for focusable elements appearing later,
 * which is fine because every overlay here renders its controls up front.
 */
export function useFocusTrap(ref: React.RefObject<HTMLElement>): void {
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const node = ref.current
    // Only claim focus if the content has not already taken it — ExpenseForm autoFocuses its
    // amount field, and that choice has to win.
    if (node != null && !node.contains(document.activeElement)) {
      const first = node.querySelector<HTMLElement>(FOCUSABLE)
      ;(first ?? node).focus()
    }

    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || ref.current == null) return
      const items = [...ref.current.querySelectorAll<HTMLElement>(FOCUSABLE)].filter(
        (element) => element.offsetParent !== null,
      )
      const first = items[0]
      const last = items[items.length - 1]
      if (first == null || last == null) return
      const active = document.activeElement
      if (event.shiftKey && (active === first || !ref.current.contains(active))) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && active === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      opener?.focus?.()
    }
  }, [ref])
}

/**
 * Modal dialog.
 *
 * Actions render in a bordered footer after the form rather than floating over it — the
 * overlapping cancel button was a real bug on the mobile Add Transaction screen. That is also
 * why the mobile bottom-sheet treatment in index.css does NOT make the footer sticky.
 *
 * Two behaviours are newer than the rest of this component and are intentional: the page
 * behind it no longer scrolls, and closing returns focus to the control that opened it
 * instead of dropping it on <body>.
 */
export function Modal({
  title,
  subtitle,
  onClose,
  children,
  actions,
}: {
  title: string
  subtitle?: ReactNode
  onClose: () => void
  children: ReactNode
  actions?: ReactNode
}) {
  const panel = useRef<HTMLDivElement>(null)
  useEscapeToClose(onClose)
  useScrollLock()
  useFocusTrap(panel)

  return (
    <div
      className="backdrop"
      onMouseDown={(event) => {
        // Only a click that both starts and ends on the backdrop dismisses, so a drag that
        // began inside the dialog does not close it.
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        /* So useFocusTrap has somewhere to put focus when the dialog has no controls. */
        tabIndex={-1}
        ref={panel}
      >
        <h2>{title}</h2>
        {subtitle != null && <p className="sub">{subtitle}</p>}
        {children}
        {actions != null && <div className="modal-actions">{actions}</div>}
      </div>
    </div>
  )
}

/** Short date, e.g. `3 Feb 2026`. */
export const formatDate = (millis: number): string =>
  new Date(millis).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })

/** `yyyy-mm-dd` in local time, for `<input type="date">`. */
export function toDateInput(millis: number): string {
  const date = new Date(millis)
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/**
 * `yyyy-mm-dd` back to epoch millis at local midday.
 *
 * Midday rather than midnight: the date-range filter bucket by local day, and a midnight
 * value lands on the boundary where a timezone shift of even an hour moves the expense into
 * the previous day.
 */
export function fromDateInput(value: string): number {
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return Date.now()
  return new Date(year, month - 1, day, 12, 0, 0, 0).getTime()
}
