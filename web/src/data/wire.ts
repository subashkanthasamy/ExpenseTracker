/**
 * Decoding time fields off the wire.
 *
 * **Time is epoch milliseconds on this project's wire format, never a Firestore `Timestamp`.**
 * Writing a `Timestamp` crashed Android with `Field 'date' is not a java.lang.Number`, and in
 * reverse decoded every Android-written date as "now".
 *
 * But documents written before that was settled *do* hold `Timestamp`s, so every client reads
 * either representation while writing only millis. Android has
 * `FirestoreDataSource.getEpochMillis`, iOS has `FirestoreService.decodeMillis`, and this is
 * the web's counterpart — it was missing, which is the whole bug it exists to prevent:
 *
 * `Number(someTimestamp)` is `NaN`, and every comparison against `NaN` is false. So a legacy
 * row did not error, it went *quietly absent* — still counted in a list's length, but dropped
 * from every date range. That showed up as an expense list of 59 next to a dashboard of 58.
 */

interface TimestampLike {
  toMillis?: () => number
  /** The Firestore JS SDK's own field names. */
  seconds?: number
  nanoseconds?: number
  /** The admin SDK's, and what a Timestamp looks like once round-tripped through JSON. */
  _seconds?: number
  _nanoseconds?: number
}

/** Epoch millis for a wire value, or [fallback] when it cannot be read as a time at all. */
export function millis(value: unknown, fallback = 0): number {
  if (value == null) return fallback

  if (typeof value === 'number') return Number.isFinite(value) ? value : fallback

  // A hand-edited document, or a value that has been through a JSON round trip.
  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : fallback
  }

  if (value instanceof Date) {
    const time = value.getTime()
    return Number.isFinite(time) ? time : fallback
  }

  if (typeof value === 'object') {
    const timestamp = value as TimestampLike
    if (typeof timestamp.toMillis === 'function') {
      const time = timestamp.toMillis()
      if (Number.isFinite(time)) return time
    }
    const seconds = timestamp.seconds ?? timestamp._seconds
    if (typeof seconds === 'number' && Number.isFinite(seconds)) {
      const nanos = timestamp.nanoseconds ?? timestamp._nanoseconds ?? 0
      return seconds * 1000 + Math.floor(nanos / 1_000_000)
    }
  }

  return fallback
}

/**
 * Epoch millis, or null.
 *
 * For genuinely optional fields — `targetDate`, `endDate`, `lastGeneratedDate` — which the
 * mobile clients omit rather than writing null. An absent field must stay null, not become 0,
 * or "no target date" turns into 1 January 1970.
 */
export function millisOrNull(value: unknown): number | null {
  if (value == null) return null
  const time = millis(value, Number.NaN)
  return Number.isFinite(time) ? time : null
}
