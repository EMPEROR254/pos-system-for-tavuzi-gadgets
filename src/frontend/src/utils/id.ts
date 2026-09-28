// Random ids made in the browser (e.g. to stop a sale being saved twice).
// crypto.randomUUID only works on https or localhost; the shop's tills may use
// plain http on the local network, so fall back to getRandomValues.
export function randomId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}
