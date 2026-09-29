// Supplier dialogs: add/edit a supplier, record a payment, and the statement
// (deliveries and payments with the running balance owed).
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { SelectField, TextField } from '../../components/Field'
import { ListError, ListSkeleton } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import {
  createSupplier,
  paySupplier,
  SUPPLIER_PAYMENT_LABELS,
  supplierStatement,
  updateSupplier,
  type Supplier,
  type SupplierPaymentMethod,
} from '../../services/purchases'
import { formatDate, formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'

function Problem({ text }: { text: string | null }) {
  return text ? (
    <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
      {text}
    </p>
  ) : null
}

export function SupplierDialog({ supplier, onClose }: { supplier: Supplier | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(supplier?.name ?? '')
  const [phone, setPhone] = useState(supplier?.phone ?? '')
  const [email, setEmail] = useState(supplier?.email ?? '')
  const [notes, setNotes] = useState(supplier?.notes ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!name.trim()) return setError("Enter the supplier's name.")
    setBusy(true)
    try {
      const body = { name, phone, email: email || undefined, notes, active: supplier?.active ?? true }
      if (supplier) await updateSupplier(supplier.id, body)
      else await createSupplier(body)
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success(`Saved ${name.trim()}.`)
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      title={supplier ? 'Edit supplier' : 'Add supplier'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button busy={busy} onClick={() => void save()}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Problem text={error} />
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <TextField label="Phone (optional)" inputMode="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <TextField label="Email (optional)" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>
    </Dialog>
  )
}

export function PayDialog({ supplier, onClose }: { supplier: Supplier; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(String(supplier.balance))
  const [method, setMethod] = useState<SupplierPaymentMethod>('MPESA')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    const value = Number(amount)
    if (!(value > 0)) return setError('Enter the amount paid.')
    setBusy(true)
    try {
      const updated = await paySupplier(supplier.id, { amount: value, method, reference: reference || undefined })
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success(`Payment saved. You now owe ${supplier.name} ${formatKes(updated.balance)}.`)
      onClose()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      title={`Pay ${supplier.name}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button busy={busy} onClick={() => void save()}>
            Save payment
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Problem text={error} />
        <p className="text-sm text-muted">
          You owe <span className="font-semibold text-foreground tabular-nums">{formatKes(supplier.balance)}</span>
        </p>
        <TextField
          label="Amount (KES)"
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <SelectField
          label="Paid by"
          value={method}
          onChange={(e) => setMethod(e.target.value as SupplierPaymentMethod)}
        >
          {(Object.keys(SUPPLIER_PAYMENT_LABELS) as SupplierPaymentMethod[]).map((m) => (
            <option key={m} value={m}>
              {SUPPLIER_PAYMENT_LABELS[m]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Reference (optional)"
          placeholder="M-Pesa code, cheque or transfer number"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
        />
      </div>
    </Dialog>
  )
}

export function StatementDialog({ supplier, onClose }: { supplier: Supplier; onClose: () => void }) {
  const statement = useQuery({
    queryKey: ['suppliers', 'statement', supplier.id],
    queryFn: () => supplierStatement(supplier.id),
  })
  return (
    <Dialog open title={`Statement · ${supplier.name}`} onClose={onClose}>
      {statement.isPending ? (
        <ListSkeleton label="Loading statement" />
      ) : statement.isError ? (
        <ListError what="the statement" error={statement.error} onRetry={() => void statement.refetch()} />
      ) : statement.data.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No deliveries or payments yet.</p>
      ) : (
        <ul className="divide-y divide-border text-sm">
          {statement.data.map((l, i) => (
            <li key={i} className="flex items-start justify-between gap-3 py-2">
              <div>
                <p className="font-medium">{l.description}</p>
                <p className="text-xs text-muted">{formatDate(l.date)}</p>
              </div>
              <div className="text-right tabular-nums">
                <p className={l.amount < 0 ? 'text-success' : ''}>{formatKes(l.amount)}</p>
                <p className="text-xs text-muted">owed {formatKes(l.balance)}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  )
}
