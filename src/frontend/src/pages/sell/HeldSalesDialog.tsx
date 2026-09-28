// Paused sales on this till: pick one up again or throw it away.
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { formatDateTime, formatKes } from '../../utils/format'
import { cartTotals } from './cartMath'
import { useCart } from './cartStore'

export function HeldSalesDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { held, resume, discardHeld } = useCart()
  return (
    <Dialog open={open} title="Paused sales" onClose={onClose}>
      {held.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No paused sales on this till.</p>
      ) : (
        <ul className="space-y-3">
          {held.map((h) => {
            const totals = cartTotals(h.cart.lines, h.cart.priceTier)
            return (
              <li key={h.id} className="rounded-xl border border-border p-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-medium">{h.label}</p>
                    <p className="text-xs text-muted">
                      {formatDateTime(h.heldAt)} · {totals.items} item{totals.items === 1 ? '' : 's'}
                    </p>
                  </div>
                  <p className="font-semibold tabular-nums">{formatKes(totals.total)}</p>
                </div>
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button
                    onClick={() => {
                      resume(h.id)
                      onClose()
                    }}
                  >
                    Continue
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => {
                      if (window.confirm(`Throw away "${h.label}"?`)) discardHeld(h.id)
                    }}
                  >
                    Discard
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Dialog>
  )
}
