// Stock dialogs: change stock by hand (opening stock, damaged, parts used for
// warranty repairs...) and a product's stock history.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { SelectField, TextField } from '../../components/Field'
import { ListError, ListSkeleton } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import {
  adjustStock,
  REASON_LABELS,
  serialsInStock,
  stockHistory,
  type AdjustmentReason,
  type StockRow,
} from '../../services/stock'
import { formatDateTime } from '../../utils/format'
import { toast } from '../../utils/toast'

const TYPE_LABELS = { SALE: 'Sold', RETURN: 'Returned', PURCHASE: 'Delivery', ADJUSTMENT: 'Adjusted', COUNT: 'Count' }

export function AdjustDialog({ row, onClose }: { row: StockRow; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [direction, setDirection] = useState<'add' | 'remove'>('add')
  const [quantity, setQuantity] = useState('1')
  const [reason, setReason] = useState<AdjustmentReason>('OPENING_STOCK')
  const [note, setNote] = useState('')
  const [serialText, setSerialText] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const tracked = row.serialType !== 'NONE'
  const onShelf = useQuery({
    queryKey: ['stock', 'serials', row.productId],
    queryFn: () => serialsInStock(row.productId),
    enabled: tracked,
  })

  // For IMEI products the quantity is the number of IMEIs typed or ticked.
  const typed = serialText.split(/[\s,]+/).filter(Boolean)
  const count = tracked ? (direction === 'add' ? typed.length : picked.length) : Number(quantity) || 0

  const save = async () => {
    if (count <= 0) return setError(tracked ? 'List at least one IMEI/serial.' : 'Enter how many.')
    setBusy(true)
    setError(null)
    try {
      const result = await adjustStock({
        productId: row.productId,
        quantity: direction === 'add' ? count : -count,
        reason,
        note: note.trim() || undefined,
        serials: tracked ? (direction === 'add' ? typed : picked) : undefined,
      })
      await queryClient.invalidateQueries({ queryKey: ['stock'] })
      toast.success(`${row.name}: now ${result.stockOnHand} in stock.`)
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
      title={`Adjust stock · ${row.name}`}
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
        {error && (
          <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
            {error}
          </p>
        )}
        <p className="text-sm text-muted">
          Now in stock: <span className="font-semibold text-foreground tabular-nums">{row.stockOnHand}</span>{' '}
          {row.baseUnit.toLowerCase()}
        </p>
        <div
          role="radiogroup"
          aria-label="Add or remove"
          className="grid grid-cols-2 rounded-xl bg-background p-1 text-sm"
        >
          {(['add', 'remove'] as const).map((d) => (
            <button
              key={d}
              type="button"
              role="radio"
              aria-checked={direction === d}
              onClick={() => {
                setDirection(d)
                setReason(d === 'add' ? 'OPENING_STOCK' : 'DAMAGED')
              }}
              className={`min-h-10 rounded-lg font-medium ${direction === d ? 'bg-card shadow-sm' : 'text-muted'}`}
            >
              {d === 'add' ? 'Add stock' : 'Remove stock'}
            </button>
          ))}
        </div>
        {!tracked ? (
          <TextField
            label="How many"
            inputMode="numeric"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        ) : direction === 'add' ? (
          <label className="block space-y-1.5 text-sm">
            <span className="font-medium">IMEIs / serial numbers (one per line or scan them)</span>
            <textarea
              aria-label="IMEIs or serial numbers to add"
              rows={4}
              value={serialText}
              onChange={(e) => setSerialText(e.target.value)}
              className="block w-full rounded-xl border border-border bg-background p-3 font-mono text-base sm:text-sm"
            />
            <span className="text-xs text-muted">{typed.length} listed</span>
          </label>
        ) : (
          <fieldset className="space-y-1 text-sm">
            <legend className="font-medium">Which ones are leaving the shelf?</legend>
            {(onShelf.data ?? []).length === 0 && <p className="text-muted">None on the shelf.</p>}
            {(onShelf.data ?? []).map((s) => (
              <label key={s} className="flex min-h-11 items-center gap-2 font-mono">
                <input
                  type="checkbox"
                  className="size-5 accent-brand"
                  checked={picked.includes(s)}
                  onChange={(e) => setPicked(e.target.checked ? [...picked, s] : picked.filter((x) => x !== s))}
                />
                {s}
              </label>
            ))}
          </fieldset>
        )}
        <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as AdjustmentReason)}>
          {(Object.keys(REASON_LABELS) as AdjustmentReason[]).map((r) => (
            <option key={r} value={r}>
              {REASON_LABELS[r]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Note (optional)"
          placeholder={reason === 'WARRANTY_REPAIR' ? 'e.g. Screen for receipt TG000123' : ''}
          maxLength={200}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
    </Dialog>
  )
}

export function HistoryDialog({ row, onClose }: { row: StockRow; onClose: () => void }) {
  const history = useQuery({
    queryKey: ['stock', 'history', row.productId],
    queryFn: () => stockHistory(row.productId),
  })
  return (
    <Dialog open title={`Stock history · ${row.name}`} onClose={onClose}>
      {history.isPending ? (
        <ListSkeleton label="Loading history" />
      ) : history.isError ? (
        <ListError what="the history" error={history.error} onRetry={() => void history.refetch()} />
      ) : history.data.content.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No stock changes yet.</p>
      ) : (
        <ul className="divide-y divide-border text-sm">
          {history.data.content.map((m) => (
            <li key={m.id} className="flex items-start justify-between gap-3 py-2">
              <div>
                <p className="font-medium">
                  {TYPE_LABELS[m.type]}
                  {m.reason && ` · ${REASON_LABELS[m.reason as AdjustmentReason] ?? m.reason}`}
                  {m.reference && <span className="text-muted"> · {m.reference}</span>}
                </p>
                <p className="text-xs text-muted">
                  {formatDateTime(m.createdAt)} · {m.userName}
                  {m.note && ` · ${m.note}`}
                </p>
              </div>
              <div className="text-right tabular-nums">
                <p className={m.quantity < 0 ? 'text-danger' : 'text-success'}>
                  {m.quantity > 0 ? '+' : ''}
                  {m.quantity}
                </p>
                <p className="text-xs text-muted">= {m.balanceAfter}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  )
}
