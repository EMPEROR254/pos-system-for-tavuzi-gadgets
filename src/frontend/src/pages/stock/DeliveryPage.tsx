// Receive a delivery from a supplier: what came in (in any unit, e.g. cartons),
// the cost, the IMEIs of phones, and what was paid now. The rest is owed.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass, SelectField, TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import { lookupProducts, type SellableProduct } from '../../services/products'
import {
  listSuppliers,
  receiveDelivery,
  SUPPLIER_PAYMENT_LABELS,
  type SupplierPaymentMethod,
} from '../../services/purchases'
import { formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'
import { useDebounced } from '../../utils/useDebounced'
import { StockNav } from './StockNav'

type Line = {
  key: number
  product: SellableProduct
  unitId: number
  quantity: string
  unitCost: string
  serials: string
}

let nextKey = 1
const splitSerials = (text: string) => text.split(/[\s,]+/).filter(Boolean)

export function deliveryTotal(lines: Line[]): number {
  return (
    lines.reduce((sum, l) => sum + Math.round((Number(l.quantity) || 0) * (Number(l.unitCost) || 0) * 100), 0) / 100
  )
}

export default function DeliveryPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const suppliers = useQuery({
    queryKey: ['suppliers', 'all'],
    queryFn: () => listSuppliers({ active: true, size: 100 }),
  })
  const [supplierId, setSupplierId] = useState('')
  const [invoiceNo, setInvoiceNo] = useState('')
  const [lines, setLines] = useState<Line[]>([])
  const [search, setSearch] = useState('')
  const [paid, setPaid] = useState('0')
  const [method, setMethod] = useState<SupplierPaymentMethod>('MPESA')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const q = useDebounced(search.trim(), 250)
  const found = useQuery({ queryKey: ['product-lookup', q], queryFn: () => lookupProducts(q), enabled: q.length >= 2 })
  const total = deliveryTotal(lines)

  const add = (product: SellableProduct) => {
    setLines([
      ...lines,
      { key: nextKey++, product, unitId: product.units[0].id, quantity: '1', unitCost: '', serials: '' },
    ])
    setSearch('')
  }
  const update = (key: number, changes: Partial<Line>) =>
    setLines(lines.map((l) => (l.key === key ? { ...l, ...changes } : l)))

  const save = async () => {
    if (!supplierId) return setError('Choose the supplier.')
    if (lines.length === 0) return setError('Add what was delivered.')
    setBusy(true)
    setError(null)
    try {
      const result = await receiveDelivery({
        supplierId: Number(supplierId),
        invoiceNo: invoiceNo || undefined,
        lines: lines.map((l) => ({
          productId: l.product.id,
          unitId: l.unitId,
          quantity: Number(l.quantity),
          unitCost: Number(l.unitCost) || 0,
          serials: l.product.serialType === 'NONE' ? undefined : splitSerials(l.serials),
        })),
        amountPaid: Number(paid) || 0,
        paymentMethod: Number(paid) > 0 ? method : undefined,
        paymentReference: reference || undefined,
      })
      await queryClient.invalidateQueries({ queryKey: ['stock'] })
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success(`Delivery of ${formatKes(result.total)} received. Stock updated.`)
      navigate('/stock/suppliers')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not save the delivery.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Receive delivery</h1>
      <StockNav />
      {error && (
        <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
          {error}
        </p>
      )}
      <div className="grid gap-4 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-2">
        <SelectField label="Supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
          <option value="">Choose…</option>
          {(suppliers.data?.content ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Supplier invoice no. (optional)"
          value={invoiceNo}
          onChange={(e) => setInvoiceNo(e.target.value)}
        />
      </div>

      <div className="relative">
        <input
          aria-label="Add a product to the delivery"
          type="search"
          placeholder="Add a product: search or scan"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={inputClass}
        />
        {q.length >= 2 && (found.data ?? []).length > 0 && (
          <ul className="absolute inset-x-0 top-full z-20 mt-1 max-h-72 overflow-y-auto rounded-xl border border-border bg-card shadow-lg">
            {(found.data ?? []).map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => add(p)}
                  className="min-h-11 w-full px-3 py-2 text-left hover:bg-background"
                >
                  {p.name} <span className="text-xs text-muted">· {p.stockOnHand} in stock</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <ul className="space-y-3">
        {lines.map((l) => {
          const unit = l.product.units.find((u) => u.id === l.unitId) ?? l.product.units[0]
          const needed = l.product.serialType === 'NONE' ? 0 : (Number(l.quantity) || 0) * unit.factor
          return (
            <li key={l.key} className="space-y-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium">{l.product.name}</p>
                <button
                  type="button"
                  onClick={() => setLines(lines.filter((x) => x.key !== l.key))}
                  className="min-h-11 rounded-lg px-3 text-sm text-danger hover:bg-danger-soft"
                >
                  Remove
                </button>
              </div>
              <div className="grid grid-cols-3 gap-2">
                <select
                  aria-label={`Unit for ${l.product.name}`}
                  value={l.unitId}
                  onChange={(e) => update(l.key, { unitId: Number(e.target.value) })}
                  className={inputClass}
                >
                  {l.product.units.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name}
                    </option>
                  ))}
                </select>
                <input
                  aria-label={`Quantity of ${l.product.name}`}
                  inputMode="numeric"
                  value={l.quantity}
                  onChange={(e) => update(l.key, { quantity: e.target.value })}
                  className={`${inputClass} text-right`}
                />
                <input
                  aria-label={`Cost per ${unit.name} of ${l.product.name}`}
                  placeholder={`Cost per ${unit.name.toLowerCase()}`}
                  inputMode="decimal"
                  value={l.unitCost}
                  onChange={(e) => update(l.key, { unitCost: e.target.value })}
                  className={`${inputClass} text-right`}
                />
              </div>
              {needed > 0 && (
                <label className="block space-y-1 text-sm">
                  <span>
                    IMEIs / serials ({splitSerials(l.serials).length} of {needed}) - scan or type, one per line
                  </span>
                  <textarea
                    aria-label={`IMEIs for ${l.product.name}`}
                    rows={Math.min(needed, 6)}
                    value={l.serials}
                    onChange={(e) => update(l.key, { serials: e.target.value })}
                    className="block w-full rounded-xl border border-border bg-background p-3 font-mono text-base sm:text-sm"
                  />
                </label>
              )}
            </li>
          )
        })}
      </ul>

      <div className="space-y-4 rounded-2xl border border-border bg-card p-4 shadow-sm">
        <p className="flex justify-between text-lg font-semibold">
          <span>Delivery total</span>
          <span className="tabular-nums">{formatKes(total)}</span>
        </p>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            label="Paid now (KES)"
            inputMode="decimal"
            value={paid}
            onChange={(e) => setPaid(e.target.value)}
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
          <TextField label="Reference (optional)" value={reference} onChange={(e) => setReference(e.target.value)} />
        </div>
        <p className="text-sm text-muted">
          Owed to the supplier after this: {formatKes(Math.max(0, total - (Number(paid) || 0)))}
        </p>
        <Button busy={busy} className="w-full sm:w-auto" onClick={() => void save()}>
          Receive delivery
        </Button>
      </div>
    </div>
  )
}
