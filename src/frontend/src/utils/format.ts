// Formatting helpers used across screens.

/** 1250 → "KES 1,250" */
export function formatKes(amount: number): string {
  return `KES ${new Intl.NumberFormat('en-KE').format(amount)}`
}
