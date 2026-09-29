// Stock rules shared by screens.

/** Spec: stock is counted weekly - flag it when the last count is older than that. */
const COUNT_EVERY_DAYS = 7

export function countIsDue(lastCountAt: string | null, now = new Date()): boolean {
  if (!lastCountAt) return true
  return now.getTime() - new Date(lastCountAt).getTime() > COUNT_EVERY_DAYS * 24 * 60 * 60 * 1000
}
