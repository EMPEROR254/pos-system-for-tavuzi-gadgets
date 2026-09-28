// Return items from a sale. Spec: "No exchanges unless supervised by me" - so
// the owner approves every return (signed in, or typing their password here).
// For an exchange: return the item, then sell the new one.
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApprovalFields } from '../../components/ApprovalFields'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { inputClass, SelectField, TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import { PAYMENT_LABELS, returnItems, type Approval, type PaymentMethod, type Sale } from '../../services/sales'
import { useSession } from '../../services/session'
import { formatKes } from '../../utils/format'
import { can } from '../../utils/permissions'
import { toast } from '../../utils/toast'

type Props = { sale: Sale; open: boolean; onClose: () => void }

export function ReturnDialog({ sale, open, onClose }: Props) {
  const user = useSession((s) => s.user)
  const queryClient = useQueryClient()
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [serials, setSerials] = useState<Record<number, string[]>>({})
  const [reason, setReason] = useState('')
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [approval, setApproval] = useState<Approval>({ username: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const needsOwner = !can(user, 'REFUND')

  const chosen = sale.lines.filter((l) => (quantities[l.id] ?? 0) > 0)
  const estimate = chosen.reduce((sum, l) => sum + (l.lineTotal / l.quantity) * quantities[l.id], 0)

  const submit = async () => {
    if (chosen.length === 0) return setError('Choose what is being returned.')
    if (!reason.trim()) return setError('Write why it is being returned.')
    setBusy(true)
    setError(null)
    try {
      const result = await returnItems(sale.id, {
        lines: chosen.map((l) => ({ saleLineId: l.id, quantity: quantities[l.id], serials: serials[l.id] })),
        reason: reason.trim(),
        refundMethod: method,
        approval: needsOwner ? approval : undefined,
      })
      await queryClient.invalidateQueries({ queryKey: ['receipt', sale.id] })
      toast.success(`Return saved. Give back ${formatKes(result.refundAmount)} by ${PAYMENT_LABELS[method]}.`)
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save the return.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title={`Return items · ${sale.receiptNo}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" busy={busy} onClick={() => void submit()}>
            Save return
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        {error && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
            {error}
          </p>
        )}
        <ul className="space-y-3">
          {sale.lines.map((l) => {
            const left = l.quantity - l.returnedQuantity
            const activeSerials = l.serials.filter((s) => !s.returned).map((s) => s.serial)
            const qty = quantities[l.id] ?? 0
            return (
              <li key={l.id} className="rounded-xl border border-border p-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="font-medium">{l.productName}</p>
                    <p className="text-muted">
                      {left} of {l.quantity} {l.unitName.toLowerCase()} can be returned
                    </p>
                  </div>
                  <input
                    aria-label={`Quantity of ${l.productName} to return`}
                    type="number"
                    min={0}
                    max={left}
                    disabled={left === 0}
                    value={qty}
                    onChange={(e) =>
                      setQuantities({ ...quantities, [l.id]: Math.max(0, Math.min(left, Number(e.target.value) || 0)) })
                    }
                    className={`${inputClass} w-20 text-right`}
                  />
                </div>
                {qty > 0 && activeSerials.length > 0 && qty < left && (
                  <fieldset className="mt-2 space-y-1">
                    <legend className="text-muted">Which ones came back?</legend>
                    {activeSerials.map((s) => (
                      <label key={s} className="flex min-h-11 items-center gap-2 font-mono">
                        <input
                          type="checkbox"
                          className="size-5 accent-brand"
                          checked={serials[l.id]?.includes(s) ?? false}
                          onChange={(e) => {
                            const current = serials[l.id] ?? []
                            setSerials({
                              ...serials,
                              [l.id]: e.target.checked ? [...current, s] : current.filter((x) => x !== s),
                            })
                          }}
                        />
                        {s}
                      </label>
                    ))}
                  </fieldset>
                )}
              </li>
            )
          })}
        </ul>
        <TextField label="Reason" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />
        <SelectField
          label="Give the money back by"
          value={method}
          onChange={(e) => setMethod(e.target.value as PaymentMethod)}
        >
          {(Object.keys(PAYMENT_LABELS) as PaymentMethod[]).map((m) => (
            <option key={m} value={m}>
              {PAYMENT_LABELS[m]}
            </option>
          ))}
        </SelectField>
        <p className="text-sm">
          About <span className="font-semibold tabular-nums">{formatKes(Math.round(estimate * 100) / 100)}</span> goes
          back to the customer.
        </p>
        {needsOwner && <ApprovalFields who="the owner" value={approval} onChange={setApproval} />}
      </div>
    </Dialog>
  )
}
