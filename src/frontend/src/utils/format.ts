// Formatting helpers used across screens.

/** 1250 → "KES 1,250"; 1250.5 → "KES 1,250.50" */
export function formatKes(amount: number): string {
  const whole = Number.isInteger(Math.round(amount * 100) / 100)
  const digits = whole ? 0 : 2
  return `KES ${new Intl.NumberFormat('en-KE', { minimumFractionDigits: digits, maximumFractionDigits: 2 }).format(amount)}`
}

/** "2026-09-28T07:00:00Z" → "28 Sept 2026" in Kenyan time (the database keeps UTC). */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-KE', { dateStyle: 'medium', timeZone: 'Africa/Nairobi' }).format(new Date(iso))
}

/** "2026-09-28T07:00:00Z" → "28 Sept 2026, 10:00" in Kenyan time. */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat('en-KE', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Africa/Nairobi',
  }).format(new Date(iso))
}

/** Today's date in Nairobi as YYYY-MM-DD (for date inputs and API filters). */
export function todayInNairobi(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Nairobi' }).format(new Date())
}
