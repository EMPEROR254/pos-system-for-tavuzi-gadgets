// The till: find products, build the cart, pause/resume sales, take payment.
// On a laptop the cart is on the left and the totals on the right; on a phone
// the totals and Pay button stay pinned at the bottom.
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ApiError } from '../../services/api'
import { getPrinter } from '../../services/hardware'
import { isUnreachable, useOffline } from '../../services/offline'
import {
  checkout,
  PAYMENT_LABELS,
  printReceipt,
  type Approval,
  type CheckoutPayment,
  type CheckoutRequest,
} from '../../services/sales'
import { useSession } from '../../services/session'
import { formatKes } from '../../utils/format'
import { can } from '../../utils/permissions'
import { toast } from '../../utils/toast'
import { CartLineRow } from './CartLineRow'
import { cartProblems, cartTotals, lineAmounts, unitOf } from './cartMath'
import { CustomerPicker } from './CustomerPicker'
import { useCart } from './cartStore'
import { HeldSalesDialog } from './HeldSalesDialog'
import { PaymentDialog } from './PaymentDialog'
import { ProductSearch } from './ProductSearch'
import { ShiftBar } from './ShiftBar'

export default function SellPage() {
  const user = useSession((s) => s.user)
  const { cart, held, add, update, remove, setTier, setCustomer, clear, hold } = useCart()
  const navigate = useNavigate()
  const [paying, setPaying] = useState(false)
  const [showHeld, setShowHeld] = useState(false)
  const [busy, setBusy] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)
  const [showProblems, setShowProblems] = useState(false)
  const printer = useQuery({ queryKey: ['hardware', 'printer'], queryFn: getPrinter, staleTime: 5 * 60_000 })

  const totals = cartTotals(cart.lines, cart.priceTier)
  const problems = cartProblems(cart.lines, cart.priceTier)
  const needsApproval = totals.discount > 0 && !can(user, 'GIVE_DISCOUNT')

  const startPayment = () => {
    setShowProblems(true)
    if (cart.lines.length === 0 || problems.length > 0) return
    setPayError(null)
    setPaying(true)
  }

  const pay = async (payments: CheckoutPayment[], approval?: Approval) => {
    setBusy(true)
    setPayError(null)
    // The cart's clientRef makes a retry safe: the server never saves it twice.
    const request: CheckoutRequest = {
      clientRef: cart.clientRef,
      priceTier: cart.priceTier,
      lines: cart.lines.map((l) => ({
        productId: l.product.id,
        unitId: l.unitId,
        quantity: l.quantity,
        discount: l.discount || undefined,
        serials: l.serials.filter((s) => s.trim()),
      })),
      payments,
      customerName: cart.customerName.trim() || undefined,
      customerPhone: cart.customerPhone.trim() || undefined,
      customerId: cart.customerId ?? undefined,
      marketingOptIn: cart.marketingOptIn ?? false,
      smsReceipt: Boolean(cart.customerPhone.trim()) && (cart.smsReceipt ?? true),
      approval,
    }
    try {
      const sale = await checkout(request)
      clear()
      setPaying(false)
      setShowProblems(false)
      toast.success(`Sale ${sale.receiptNo} saved.${sale.changeDue > 0 ? ` Change: ${formatKes(sale.changeDue)}` : ''}`)
      if (printer.data?.autoPrint && printer.data.host) {
        // Printing must never block the next sale: report problems, don't wait.
        printReceipt(sale.id).catch((e: unknown) =>
          toast.error(e instanceof ApiError ? e.message : 'The receipt did not print.'),
        )
      }
      navigate(`/sales/${sale.id}/receipt?new=1`)
    } catch (e) {
      if (isUnreachable(e)) {
        saveOffline(request, payments)
      } else {
        setPayError(
          e instanceof ApiError ? e.message : 'Could not save the sale. Try again - it will not be saved twice.',
        )
      }
    } finally {
      setBusy(false)
    }
  }

  /** The server can't be reached: keep the sale on this till; it is sent when the server is back. */
  const saveOffline = (request: CheckoutRequest, payments: CheckoutPayment[]) => {
    if (request.approval) {
      // A supervisor's password must never be stored on the till, so it can't wait for later.
      setPayError('Discount approval needs the server. Remove the discount, or wait for the connection.')
      return
    }
    const paid = payments.reduce((sum, p) => sum + Math.round(p.amount * 100), 0) / 100
    useOffline.getState().add({
      clientRef: request.clientRef,
      request: { ...request, offlineCreatedAt: new Date().toISOString() },
      total: totals.total,
      itemCount: totals.items,
      cashierName: user?.fullName ?? '',
      createdAt: new Date().toISOString(),
      receipt: {
        lines: cart.lines.map((l) => ({
          name: l.product.name,
          unitName: unitOf(l).name,
          quantity: l.quantity,
          total: lineAmounts(l, cart.priceTier).total,
        })),
        payments: payments.map((p) => ({ label: PAYMENT_LABELS[p.method], amount: p.amount, reference: p.reference })),
        change: Math.max(0, Math.round((paid - totals.total) * 100) / 100),
      },
    })
    clear()
    setPaying(false)
    setShowProblems(false)
    toast.success('No connection: the sale is saved on this till and will be sent automatically.')
    navigate(`/sync/receipt/${request.clientRef}`)
  }

  const holdSale = () => {
    const label = cart.customerName.trim() || `Sale of ${formatKes(totals.total)}`
    hold(label)
    setShowProblems(false)
    toast.success(`Paused "${label}". Serve the next customer.`)
  }

  return (
    <div className="grid gap-4 pb-28 lg:grid-cols-[1fr_22rem] lg:pb-0">
      <section className="space-y-3">
        <ShiftBar />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h1 className="text-2xl font-semibold tracking-tight">Sell</h1>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setShowHeld(true)}>
              Paused ({held.length})
            </Button>
            <Button variant="secondary" disabled={cart.lines.length === 0} onClick={holdSale}>
              Pause sale
            </Button>
          </div>
        </div>
        <ProductSearch tier={cart.priceTier} onAdd={add} />
        <div className="rounded-2xl border border-border bg-card shadow-sm">
          {cart.lines.length === 0 ? (
            <p className="p-10 text-center text-sm text-muted">Search or scan to add the first item.</p>
          ) : (
            <ul aria-label="Cart">
              {cart.lines.map((line) => (
                <CartLineRow
                  key={line.key}
                  line={line}
                  tier={cart.priceTier}
                  onChange={(changes) => update(line.key, changes)}
                  onRemove={() => remove(line.key)}
                />
              ))}
            </ul>
          )}
        </div>
        {showProblems && problems.length > 0 && (
          <ul role="alert" className="space-y-1 rounded-xl bg-danger-soft p-3 text-sm text-danger">
            {problems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </section>

      <aside className="space-y-3 lg:sticky lg:top-20 lg:self-start">
        <div className="space-y-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
          <div role="radiogroup" aria-label="Prices" className="grid grid-cols-2 rounded-xl bg-background p-1 text-sm">
            {(['RETAIL', 'WHOLESALE'] as const).map((tier) => (
              <button
                key={tier}
                type="button"
                role="radio"
                aria-checked={cart.priceTier === tier}
                onClick={() => setTier(tier)}
                className={`min-h-10 rounded-lg font-medium ${
                  cart.priceTier === tier ? 'bg-card shadow-sm' : 'text-muted hover:text-foreground'
                }`}
              >
                {tier === 'RETAIL' ? 'Retail' : 'Wholesale'}
              </button>
            ))}
          </div>
          <details className="text-sm" open={Boolean(cart.customerPhone || cart.customerId)}>
            <summary className="flex min-h-11 cursor-pointer items-center text-muted">Customer (optional)</summary>
            <div className="mt-2">
              <CustomerPicker cart={cart} onChange={setCustomer} />
            </div>
          </details>
          <dl className="grid grid-cols-2 gap-y-1 text-sm">
            <dt className="text-muted">Items</dt>
            <dd className="text-right tabular-nums">{totals.items}</dd>
            {totals.discount > 0 && (
              <>
                <dt className="text-muted">Before discount</dt>
                <dd className="text-right tabular-nums">{formatKes(totals.subtotal)}</dd>
                <dt className="text-muted">Discount</dt>
                <dd className="text-right tabular-nums">−{formatKes(totals.discount)}</dd>
              </>
            )}
            <dt className="text-muted">VAT included</dt>
            <dd className="text-right tabular-nums">{formatKes(totals.vat)}</dd>
          </dl>
        </div>
        {/* Pinned to the bottom on phones so Pay is always one tap away. */}
        <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-card p-3 shadow-lg lg:static lg:rounded-2xl lg:border lg:shadow-sm">
          <div className="mx-auto flex max-w-6xl items-center gap-3 lg:block lg:space-y-3">
            <div className="flex-1 lg:flex lg:items-baseline lg:justify-between">
              <p className="text-xs text-muted lg:text-sm">Total</p>
              <p className="text-2xl font-bold tabular-nums">{formatKes(totals.total)}</p>
            </div>
            <Button
              className="min-h-12 flex-1 text-base lg:w-full"
              disabled={cart.lines.length === 0}
              onClick={startPayment}
            >
              Pay
            </Button>
            {cart.lines.length > 0 && (
              <button
                type="button"
                onClick={() => window.confirm('Clear this cart?') && clear()}
                className="hidden min-h-11 w-full rounded-xl text-sm text-muted hover:text-danger lg:block"
              >
                Clear cart
              </button>
            )}
          </div>
        </div>
      </aside>

      {paying && (
        <PaymentDialog
          open
          total={totals.total}
          needsApproval={needsApproval}
          busy={busy}
          error={payError}
          onClose={() => setPaying(false)}
          onPay={(payments, approval) => void pay(payments, approval)}
        />
      )}
      <HeldSalesDialog open={showHeld} onClose={() => setShowHeld(false)} />
    </div>
  )
}
