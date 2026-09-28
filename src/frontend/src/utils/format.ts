// Formatting helpers used across screens.

/** 1250 → "KES 1,250" */
export function formatKes(amount: number): string {
  return `KES ${new Intl.NumberFormat('en-KE').format(amount)}`
}

/** "2026-09-28T07:00:00Z" → "28 Sept 2026" in Kenyan time (the database keeps UTC). */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-KE', { dateStyle: 'medium', timeZone: 'Africa/Nairobi' }).format(new Date(iso))
}
