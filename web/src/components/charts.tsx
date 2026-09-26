/**
 * Charts, hand-rolled as inline SVG.
 *
 * No chart library, deliberately: the bundle already carries Firebase plus the Kotlin/JS
 * shared module, and `vite.config.ts` pins `preserveSymlinks: true` for the `file:` dependency
 * — a dependency added here is a dependency that has to keep working through that. Every mark
 * is a few lines of geometry and the whole file is under a Recharts import's own weight.
 *
 * Two conventions worth keeping:
 *
 * 1. **Colour comes from CSS custom properties, never from JS.** `fill="var(--accent)"` means
 *    dark mode needs no re-render and no `matchMedia` listener — the charts follow `data-theme`
 *    for free, exactly like the rest of the app.
 * 2. **Geometry is measured in real pixels, not viewBox units.** A `viewBox` that scales makes
 *    a 2px stroke render at 4px on a wide card and a 4px corner radius grow with it. The
 *    `useWidth` hook below costs one ResizeObserver and keeps every spec exact.
 *
 * Mark specs, fixed across all of them: columns capped at 24px with a 4px rounded top and a
 * square baseline; lines 2px with round joins; area washes at ~10% opacity; gridlines a solid
 * 1px hairline, never dashed; one axis only, never two.
 */
import { useLayoutEffect, useRef, useState } from 'react'

import { money, moneyShort } from '../shared'
import type { MonthPoint } from '../data/series'

/** Measures an element, so charts can work in device pixels instead of scaled viewBox units. */
function useWidth<T extends HTMLElement>(): [React.RefObject<T>, number] {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)

  useLayoutEffect(() => {
    const node = ref.current
    if (node == null) return
    setWidth(node.clientWidth)
    // ResizeObserver rather than a window resize listener: a card can change width without
    // the window doing so — the sidebar collapsing, or a grid column reflowing.
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry != null) setWidth(entry.contentRect.width)
    })
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return [ref, width]
}

/**
 * An axis maximum rounded up to something a reader can hold in their head.
 *
 * Ticks carry every value that is not directly labelled, so they have to be clean numbers —
 * 0 / 20,000 / 40,000, never 0 / 17,431 / 34,862.
 */
export function niceMax(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 1
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)))
  const normalised = value / magnitude
  const step = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 2.5 ? 2.5 : normalised <= 5 ? 5 : 10
  return step * magnitude
}

/** A column with a 4px rounded top and a square baseline, growing from `baseline`. */
function columnPath(x: number, top: number, width: number, baseline: number): string {
  const height = Math.max(0, baseline - top)
  const r = Math.min(4, width / 2, height)
  if (height <= 0.5) return ''
  return [
    `M${x},${baseline}`,
    `L${x},${top + r}`,
    `Q${x},${top} ${x + r},${top}`,
    `L${x + width - r},${top}`,
    `Q${x + width},${top} ${x + width},${top + r}`,
    `L${x + width},${baseline}`,
    'Z',
  ].join(' ')
}

/* ------------------------------------------------------------------ tooltip */

interface TipState {
  x: number
  y: number
  value: string
  label: string
}

/**
 * The hover readout.
 *
 * Value first and bold, label second and quiet — the legend's hierarchy inverted, because by
 * the time someone is hovering they know which series it is and want the number. Content is
 * passed as React children, so a user-entered category name is escaped by React rather than
 * concatenated into HTML.
 */
function ChartTip({ tip, width }: { tip: TipState; width: number }) {
  // Keep the bubble inside the card rather than letting it clip at either edge.
  const clamped = Math.min(Math.max(tip.x, 54), Math.max(width - 54, 54))
  return (
    <div className="chart-tip" style={{ left: clamped, top: tip.y - 8 }} role="status">
      <div className="tip-value">{tip.value}</div>
      <div className="tip-label">{tip.label}</div>
    </div>
  )
}

/* ---------------------------------------------------------------- sparkline */

/**
 * A bare trend line for a stat tile. No axes, no tooltip.
 *
 * The tile's own value is already the headline, so this only has to answer "rising or
 * falling" — a tooltip here would be a second way to read a number that is printed 20px
 * above it. `preserveAspectRatio="none"` lets it stretch to any tile width, and
 * `vector-effect="non-scaling-stroke"` stops the non-uniform scale from thickening the
 * stroke with it.
 */
export function Sparkline({
  values,
  height = 30,
  accent = 'var(--accent)',
}: {
  values: number[]
  height?: number
  accent?: string
}) {
  if (values.length < 2) return null

  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  const span = max - min || 1
  const stepX = 100 / (values.length - 1)
  const y = (value: number) => 2 + (1 - (value - min) / span) * (height - 4)

  const line = values.map((value, index) => `${index === 0 ? 'M' : 'L'}${index * stepX},${y(value)}`).join(' ')
  const area = `${line} L100,${height} L0,${height} Z`
  const id = `spark-${values.length}-${Math.round(max)}`

  return (
    <svg
      className="chart"
      viewBox={`0 0 100 ${height}`}
      height={height}
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          {/* A wash, never a saturated block. */}
          <stop offset="0%" stopColor={accent} stopOpacity="0.16" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${id})`} />
      <path
        d={line}
        fill="none"
        stroke={accent}
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  )
}

/* --------------------------------------------------------------- trend */

/**
 * Monthly totals as columns.
 *
 * One series, so the colour job is sequential and there is no legend — the card's heading
 * already says what is plotted, and a legend box with a single swatch would only restate it.
 * The month in progress is drawn at reduced opacity because it is a partial figure; showing
 * it at full weight invites reading a half-finished month as a fall in spending.
 */
export function TrendChart({ points, height = 168 }: { points: MonthPoint[]; height?: number }) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const [tip, setTip] = useState<TipState | null>(null)
  const [active, setActive] = useState<number | null>(null)

  const padLeft = 46
  const padRight = 8
  const padTop = 10
  const padBottom = 22
  const plotW = Math.max(0, width - padLeft - padRight)
  const plotH = Math.max(0, height - padTop - padBottom)
  const max = niceMax(Math.max(...points.map((p) => p.total), 0))
  const band = points.length > 0 ? plotW / points.length : 0
  const barW = Math.min(24, band * 0.62)
  const yOf = (value: number) => padTop + (1 - value / max) * plotH

  // Not memoised on purpose. These are only ever passed to inline DOM handlers on elements
  // that are recreated each render, so a useCallback saves nothing — and its dependency list
  // would have to track `width`, `height` and `points` to avoid computing the tooltip's
  // position from a stale scale, which is a correctness risk for no gain.
  const show = (index: number) => {
    const point = points[index]
    if (point == null) return
    setActive(index)
    setTip({
      x: padLeft + band * (index + 0.5),
      y: yOf(point.total),
      value: money(point.total),
      label: `${point.label} · ${point.count} ${point.count === 1 ? 'expense' : 'expenses'}${
        point.isCurrent ? ' · in progress' : ''
      }`,
    })
  }

  const hide = () => {
    setActive(null)
    setTip(null)
  }

  return (
    <div className="chart-wrap" ref={ref}>
      {width > 0 && (
        <svg className="chart" height={height} role="img" aria-label="Monthly shared spending">
          {/* Gridlines: solid hairlines one step off the surface, never dashed. */}
          {[0, 0.5, 1].map((fraction) => (
            <g key={fraction}>
              <line
                className="chart-grid"
                x1={padLeft}
                x2={width - padRight}
                y1={yOf(max * fraction)}
                y2={yOf(max * fraction)}
                vectorEffect="non-scaling-stroke"
              />
              <text className="chart-axis" x={padLeft - 8} y={yOf(max * fraction) + 4} textAnchor="end">
                {fraction === 0 ? '0' : moneyShort(max * fraction)}
              </text>
            </g>
          ))}

          {points.map((point, index) => {
            const x = padLeft + band * index + (band - barW) / 2
            return (
              <g
                key={point.start}
                data-chart-hit=""
                tabIndex={0}
                role="button"
                aria-label={`${point.label}: ${money(point.total)}`}
                onPointerEnter={() => show(index)}
                onPointerMove={() => show(index)}
                onPointerLeave={hide}
                onFocus={() => show(index)}
                onBlur={hide}
              >
                {/* The hit target is the whole band, not the painted column — aiming at a
                    narrow bar is a pinpoint nobody hits reliably. */}
                <rect x={padLeft + band * index} y={padTop} width={band} height={plotH} fill="transparent" />
                {active === index && (
                  <line
                    className="chart-crosshair"
                    x1={padLeft + band * (index + 0.5)}
                    x2={padLeft + band * (index + 0.5)}
                    y1={padTop}
                    y2={padTop + plotH}
                    vectorEffect="non-scaling-stroke"
                  />
                )}
                <path
                  className="chart-mark"
                  d={columnPath(x, yOf(point.total), barW, padTop + plotH)}
                  fill="var(--accent)"
                  fillOpacity={point.isCurrent ? 0.45 : 1}
                />
                <text
                  className="chart-axis"
                  x={padLeft + band * (index + 0.5)}
                  y={height - 6}
                  textAnchor="middle"
                >
                  {point.label}
                </text>
              </g>
            )
          })}
        </svg>
      )}
      {tip != null && <ChartTip tip={tip} width={width} />}
    </div>
  )
}

/* --------------------------------------------------------------- donut */

export interface DonutSlice {
  id: string
  label: string
  value: number
  color: string
}

const TAU = Math.PI * 2

/**
 * A share as a whole percentage, but never "0%" for a share that is not zero.
 *
 * `toFixed(0)` rounds ₹3,100 of ₹13 lakh to "0%", which reads as "nothing spent" beside a
 * visible amount. Anything above zero and under half a percent says "<1%" instead.
 */
export function sharePercent(share: number): string {
  if (share > 0 && share < 0.005) return '<1%'
  return `${(share * 100).toFixed(0)}%`
}

/**
 * The smallest sweep a non-zero segment is drawn with: about 3.5°, roughly 5px of arc on the
 * default donut. Below that the 2px separator gaps swallow the segment whole, so a category
 * the legend names has nothing to see and nothing to hover. The extra is taken from the
 * segments that can afford it, so the ring still closes; the exact figures stay in the
 * centre readout, the legend and the ranked list beside the chart.
 */
const MIN_SWEEP = 0.06

function arcPath(cx: number, cy: number, outer: number, inner: number, from: number, to: number): string {
  const large = to - from > Math.PI ? 1 : 0
  const p = (radius: number, angle: number) => [cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)]
  const [x0, y0] = p(outer, from)
  const [x1, y1] = p(outer, to)
  const [x2, y2] = p(inner, to)
  const [x3, y3] = p(inner, from)
  return `M${x0},${y0} A${outer},${outer} 0 ${large} 1 ${x1},${y1} L${x2},${y2} A${inner},${inner} 0 ${large} 0 ${x3},${y3} Z`
}

/**
 * Part-to-whole for spending by category.
 *
 * **Hue is not the identity channel here, and cannot be.** `CategoryPresets` in `shared/` is
 * the cross-client source of truth for category colours and must not be recoloured on the web
 * — but it was chosen as a set of recognisable per-category tints, not as a chart palette, and
 * it does not survive an adjacency check: Food `#4CAF50`, Vegetables `#7CB342`,
 * Fitness `#66BB6A` and Groceries `#8BC34A` are four near-identical greens (worst adjacent
 * pair ΔE 4.0 in OKLab for *normal* vision, and 3.2 under simulated protanopia), and
 * Transport/Milk/Water/Mobile are four near-identical blues.
 *
 * So three things are structural rather than stylistic, and removing any of them breaks the
 * chart for a real reader:
 *
 *  - the segment count is **capped** (`cappedSlices`), with the tail folded into one "Other";
 *  - a **legend is always rendered**, naming every segment in text — identity comes from the
 *    label, and the fill is only recognition;
 *  - the ranked list the caller places beside this chart is its **table twin**, so every value
 *    is readable without hovering at all.
 *
 * Labels are deliberately not drawn inside the arcs: a category name does not fit in a small
 * segment, and clipping it is worse than not having it.
 */
export function DonutChart({
  slices,
  size = 172,
  total,
  centerLabel = 'total',
}: {
  slices: DonutSlice[]
  size?: number
  total: number
  centerLabel?: string
}) {
  // The hovered or focused segment. Its figures replace the total in the centre of the ring:
  // a floating tooltip had to sit above the chart and covered the card's own title.
  const [active, setActive] = useState<DonutSlice | null>(null)
  const cx = size / 2
  const cy = size / 2
  const outer = size / 2 - 2
  const inner = outer * 0.62
  // A zero-value slice would otherwise become a zero-sweep path: invisible, but still
  // focusable and hoverable, so a keyboard user lands on a segment that is not there. It also
  // removes the awkward case of one slice at 100% while a sibling exists, where the two-arc
  // path has to draw a full circle from a start point equal to its end point.
  const drawn = slices.filter((slice) => slice.value > 0)
  const sum = drawn.reduce((s, slice) => s + slice.value, 0)

  // The caller shows an empty state; a donut of nothing would be a NaN path.
  if (sum <= 0) return null

  // Lift every small segment to MIN_SWEEP, then scale the rest down to pay for it.
  const natural = drawn.map((slice) => (slice.value / sum) * TAU)
  const lifted = natural.map((sweep) => Math.max(sweep, MIN_SWEEP))
  const small = natural.filter((sweep) => sweep < MIN_SWEEP).length * MIN_SWEEP
  const largeNatural = natural.filter((sweep) => sweep >= MIN_SWEEP).reduce((a, b) => a + b, 0)
  const scale = largeNatural > 0 ? (TAU - small) / largeNatural : 1
  const sweeps = natural.map((sweep, i) => (sweep >= MIN_SWEEP ? sweep * scale : lifted[i]!))

  let angle = -Math.PI / 2
  const arcs = drawn.map((slice, i) => {
    const sweep = sweeps[i]!
    const from = angle
    angle += sweep
    return { slice, from, to: angle, sweep }
  })

  // Matched by key, not identity: the caller rebuilds its slices on every render.
  const keyOf = (slice: DonutSlice) => slice.id || slice.label
  const show = (slice: DonutSlice) => setActive(slice)
  const hide = () => setActive(null)

  return (
    <div className="chart-wrap" style={{ width: size }}>
      <svg width={size} height={size} role="img" aria-label="Spending by category" className="chart">
        {/* A single category is a full ring: the two-arc path degenerates at a 360° sweep,
            so it is drawn as a stroked circle instead. */}
        {arcs.length === 1 && arcs[0] != null ? (
          <circle
            cx={cx}
            cy={cy}
            r={(outer + inner) / 2}
            fill="none"
            stroke={arcs[0].slice.color}
            strokeWidth={outer - inner}
          />
        ) : (
          arcs.map(({ slice, from, to }) => (
            <path
              key={slice.id || slice.label}
              className="chart-mark"
              data-chart-hit=""
              tabIndex={0}
              role="button"
              aria-label={`${slice.label}: ${money(slice.value)}`}
              d={arcPath(cx, cy, outer, inner, from, to)}
              fill={slice.color}
              /* The 2px separator is a gap in the SURFACE colour, not a border drawn around
                 the segment — a stroke in its own colour would add ink that is not data. */
              stroke="var(--card)"
              strokeWidth="2"
              opacity={active == null || keyOf(active) === keyOf(slice) ? 1 : 0.45}
              style={{ transition: 'opacity 0.15s' }}
              onPointerEnter={() => show(slice)}
              onPointerLeave={hide}
              onFocus={() => show(slice)}
              onBlur={hide}
            />
          ))
        )}
        {/* Proportional figures, not tabular: this is a standalone number, and equal-width
            digits read loose at display size. */}
        {active == null ? (
          <>
            <text x={cx} y={cy - 2} textAnchor="middle" fontSize="17" fontWeight="700" fill="var(--text-primary)">
              {moneyShort(total)}
            </text>
            <text x={cx} y={cy + 15} textAnchor="middle" fontSize="10" fill="var(--text-secondary)">
              {centerLabel}
            </text>
          </>
        ) : (
          // aria-live so a keyboard user tabbing through the segments hears each one.
          <g aria-live="polite">
            <text x={cx} y={cy - 8} textAnchor="middle" fontSize="17" fontWeight="700" fill="var(--text-primary)">
              {moneyShort(active.value)}
            </text>
            <text x={cx} y={cy + 8} textAnchor="middle" fontSize="10" fill="var(--text-secondary)">
              {truncate(active.label, 16)}
            </text>
            <text x={cx} y={cy + 22} textAnchor="middle" fontSize="10" fontWeight="600" fill="var(--text-secondary)">
              {sharePercent(active.value / sum)}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}

/** Fits a label inside the ring's hole, which is about 16 characters wide at 10px. */
function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

/** The donut's identity channel. Never optional — see the note on `DonutChart`. */
export function Legend({ slices }: { slices: DonutSlice[] }) {
  return (
    <div className="legend">
      {/* Same filter as the chart, so the legend cannot list a segment that is not drawn. */}
      {slices
        .filter((slice) => slice.value > 0)
        .map((slice) => (
          <span className="legend-item" key={slice.id || slice.label}>
            <span className="legend-swatch" style={{ background: slice.color }} aria-hidden="true" />
            <span>{slice.label}</span>
            <strong>{moneyShort(slice.value)}</strong>
          </span>
        ))}
    </div>
  )
}

/* Re-exported so a page importing charts does not also have to import from `series`. */
export type { MonthPoint }
