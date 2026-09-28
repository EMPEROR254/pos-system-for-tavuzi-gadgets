// Take payment: one or more methods (e.g. part cash, part M-Pesa). Change is
// only given on cash. M-Pesa: the cashier types the code from the customer's
// SMS. Card: the bank machine isn't connected, so its approval code is optional.
import { useState } from 'react'
import { ApprovalFields } from '../../components/ApprovalFields'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { inputClass } from '../../components/Field'
import { PAYMENT_LABELS, type Approval, type CheckoutPayment, type PaymentMethod } from '../../services/sales'
import { formatKes } from '../../utils/format'

export type PaymentRow = { key: number; method: PaymentMethod; amount: string; reference: string }

type Props = {
  open: boolean
  total: number
  needsApproval: boolean
  busy: boolean
  error: string | null
  onClose: () => void
  onPay: (payments: CheckoutPayment[], approval?: Approval) => void
}

const METHODS: PaymentMethod[] = ['CASH', 'MPESA_TILL', 'MPESA_PAYBILL', 'CARD']
const MPESA_CODE = /^[A-Z0-9]{10}$/
const toCents = (text: string) => Math.round((Number(text) || 0) * 100)

/** Checks the rows the same way the server does; returns a message or null. */
export function paymentProblem(rows: PaymentRow[], total: number): string | null {
  const totalCents = Math.round(total * 100)
  let paid = 0
  let nonCash = 0
  for (const row of rows) {
    const cents = toCents(row.amount)
    if (cents <= 0) return 'Every payment needs an amount.'
    if (row.method.startsWith('MPESA') && !MPESA_CODE.test(row.reference.trim().toUpperCase())) {
      return 'Type the 10-character M-Pesa code from the customer’s SMS.'
    }
    paid += cents
    if (row.method !== 'CASH') nonCash += cents
  }
  if (nonCash > totalCents) return 'M-Pesa and card can’t be more than the total - change is only given on cash.'
  if (paid < totalCents) return `${formatKes((totalCents - paid) / 100)} still to pay.`
  return null
}

let nextKey = 1

export function PaymentDialog({ open, total, needsApproval, busy, error, onClose, onPay }: Props) {
  const [rows, setRows] = useState<PaymentRow[]>(() => [
    { key: nextKey++, method: 'CASH', amount: String(total), reference: '' },
  ])
  const [approval, setApproval] = useState<Approval>({ username: '', password: '' })
  const [touched, setTouched] = useState(false)

  const paid = rows.reduce((sum, r) => sum + toCents(r.amount), 0) / 100
  const remaining = Math.max(0, Math.round((total - paid) * 100) / 100)
  const change = Math.max(0, Math.round((paid - total) * 100) / 100)
  const problem = paymentProblem(rows, total)

  const update = (key: number, changes: Partial<PaymentRow>) =>
    setRows((current) => current.map((r) => (r.key === key ? { ...r, ...changes } : r)))

  const addMethod = (method: PaymentMethod) =>
    setRows((current) => [
      ...current,
      { key: nextKey++, method, amount: remaining ? String(remaining) : '', reference: '' },
    ])

  const submit = () => {
    setTouched(true)
    if (problem) return
    onPay(
      rows.map((r) => ({
        method: r.method,
        amount: toCents(r.amount) / 100,
        reference: r.reference.trim() ? r.reference.trim().toUpperCase() : undefined,
      })),
      needsApproval ? approval : undefined,
    )
  }

  return (
    <Dialog
      open={open}
      title={`Take payment · ${formatKes(total)}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Back
          </Button>
          <Button onClick={submit} busy={busy}>
            Complete sale
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
          {rows.map((row) => (
            <li key={row.key} className="space-y-2 rounded-xl border border-border p-3">
              <div className="flex items-center gap-2">
                <select
                  aria-label="Payment method"
                  value={row.method}
                  onChange={(e) => update(row.key, { method: e.target.value as PaymentMethod })}
                  className={`${inputClass} flex-1`}
                >
                  {METHODS.map((m) => (
                    <option key={m} value={m}>
                      {PAYMENT_LABELS[m]}
                    </option>
                  ))}
                </select>
                <input
                  aria-label={`${PAYMENT_LABELS[row.method]} amount`}
                  inputMode="decimal"
                  value={row.amount}
                  onChange={(e) => update(row.key, { amount: e.target.value })}
                  className={`${inputClass} w-32 text-right tabular-nums`}
                />
                {rows.length > 1 && (
                  <button
                    type="button"
                    aria-label="Remove this payment"
                    onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
                    className="flex size-11 shrink-0 items-center justify-center rounded-xl text-muted hover:bg-background"
                  >
                    ✕
                  </button>
                )}
              </div>
              {row.method !== 'CASH' && (
                <input
                  aria-label={row.method === 'CARD' ? 'Card approval code (optional)' : 'M-Pesa code'}
                  placeholder={row.method === 'CARD' ? 'Card approval code (optional)' : 'M-Pesa code, e.g. SJK4H7XYZ1'}
                  autoCapitalize="characters"
                  autoComplete="off"
                  value={row.reference}
                  onChange={(e) => update(row.key, { reference: e.target.value.toUpperCase() })}
                  className={`${inputClass} font-mono uppercase`}
                />
              )}
            </li>
          ))}
        </ul>
        <div className="flex flex-wrap gap-2">
          {METHODS.map((m) => (
            <Button key={m} variant="secondary" onClick={() => addMethod(m)}>
              + {PAYMENT_LABELS[m]}
            </Button>
          ))}
        </div>

        <dl className="grid grid-cols-2 gap-1 rounded-xl bg-background p-3 text-sm">
          <dt className="text-muted">Paid</dt>
          <dd className="text-right tabular-nums">{formatKes(paid)}</dd>
          {remaining > 0 ? (
            <>
              <dt className="font-medium text-danger">Still to pay</dt>
              <dd className="text-right font-medium text-danger tabular-nums">{formatKes(remaining)}</dd>
            </>
          ) : (
            <>
              <dt className="text-lg font-semibold">Change</dt>
              <dd className="text-right text-lg font-semibold tabular-nums">{formatKes(change)}</dd>
            </>
          )}
        </dl>
        {touched && problem && <p className="text-sm text-danger">{problem}</p>}

        {needsApproval && (
          <>
            <p className="text-sm text-muted">This sale has a discount, so a manager or the owner must approve it.</p>
            <ApprovalFields who="a manager or the owner" value={approval} onChange={setApproval} />
          </>
        )}
      </div>
    </Dialog>
  )
}
