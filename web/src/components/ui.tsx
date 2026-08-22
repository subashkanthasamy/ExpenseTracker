/** Shared presentational primitives. */
import { useEffect, type ReactNode } from 'react'

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
}: {
  label: string
  value: string
  sub?: ReactNode
  accent?: string
}) {
  return (
    <Card>
      <div className="stat-label">{label}</div>
      <div className="stat-value" style={{ color: accent }}>
        {value}
      </div>
      {sub != null && <div className="stat-sub">{sub}</div>}
    </Card>
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

/**
 * Modal dialog.
 *
 * Actions render in a bordered footer after the form rather than floating over it — the
 * overlapping cancel button was a real bug on the mobile Add Transaction screen.
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
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="backdrop"
      onMouseDown={(event) => {
        // Only a click that both starts and ends on the backdrop dismisses, so a drag that
        // began inside the dialog does not close it.
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
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
