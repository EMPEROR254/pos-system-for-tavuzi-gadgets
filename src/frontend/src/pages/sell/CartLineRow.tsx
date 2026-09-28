// One line in the till's cart: unit, quantity, discount, IMEIs/serials and
// the line total. No animations here - this is the busiest screen.
import type { PriceTier } from '../../services/sales'
import { formatKes } from '../../utils/format'
import { lineAmounts, serialsNeeded, unitOf, unitPrice, type CartLine } from './cartMath'

type Props = {
  line: CartLine
  tier: PriceTier
  onChange: (changes: Partial<Pick<CartLine, 'quantity' | 'unitId' | 'discount' | 'serials'>>) => void
  onRemove: () => void
}

const small =
  'min-h-11 rounded-lg border border-border bg-background px-2 text-base tabular-nums focus:border-brand sm:text-sm'

export function CartLineRow({ line, tier, onChange, onRemove }: Props) {
  const unit = unitOf(line)
  const amounts = lineAmounts(line, tier)
  const needed = serialsNeeded(line)
  const name = line.product.name

  const setQuantity = (quantity: number) => {
    if (!Number.isFinite(quantity) || quantity < 1) return
    onChange({ quantity: Math.min(Math.floor(quantity), 10000) })
  }

  return (
    <li className="space-y-3 border-b border-border p-3 last:border-0">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">{name}</p>
          <p className="text-xs text-muted tabular-nums">
            {formatKes(unitPrice(unit, tier))} per {unit.name.toLowerCase()}
            {line.product.vatRate > 0 ? ` · incl. VAT ${line.product.vatRate}%` : ' · no VAT'}
          </p>
        </div>
        <div className="text-right">
          <p className="font-semibold tabular-nums">{formatKes(amounts.total)}</p>
          {amounts.discount > 0 && (
            <p className="text-xs text-muted tabular-nums line-through">{formatKes(amounts.gross)}</p>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {line.product.units.length > 1 && (
          <select
            aria-label={`Unit for ${name}`}
            value={line.unitId}
            onChange={(e) => onChange({ unitId: Number(e.target.value) })}
            className={small}
          >
            {line.product.units.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </select>
        )}
        <div className="flex items-center">
          <button
            type="button"
            aria-label={`One less ${name}`}
            onClick={() => (line.quantity > 1 ? setQuantity(line.quantity - 1) : onRemove())}
            className="size-11 rounded-l-lg border border-border bg-card text-lg hover:bg-background"
          >
            −
          </button>
          <input
            aria-label={`Quantity of ${name}`}
            inputMode="numeric"
            value={line.quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            className="h-11 w-14 border-y border-border bg-background text-center text-base tabular-nums sm:text-sm"
          />
          <button
            type="button"
            aria-label={`One more ${name}`}
            onClick={() => setQuantity(line.quantity + 1)}
            className="size-11 rounded-r-lg border border-border bg-card text-lg hover:bg-background"
          >
            +
          </button>
        </div>
        <label className="flex items-center gap-1 text-sm text-muted">
          Discount
          <input
            aria-label={`Discount on ${name} (KES)`}
            inputMode="decimal"
            placeholder="0"
            value={line.discount || ''}
            onChange={(e) => {
              const value = Number(e.target.value)
              if (Number.isFinite(value) && value >= 0) onChange({ discount: value })
            }}
            className={`${small} w-24 text-right`}
          />
        </label>
        <button
          type="button"
          onClick={onRemove}
          className="ml-auto min-h-11 rounded-lg px-3 text-sm text-danger hover:bg-danger-soft"
        >
          Remove
        </button>
      </div>

      {needed > 0 && (
        <div className="grid gap-2 sm:grid-cols-2">
          {Array.from({ length: needed }, (_, i) => (
            <input
              key={i}
              aria-label={`${line.product.serialType === 'IMEI' ? 'IMEI' : 'Serial number'} ${i + 1} for ${name}`}
              placeholder={line.product.serialType === 'IMEI' ? `IMEI ${i + 1} (15 digits)` : `Serial number ${i + 1}`}
              inputMode={line.product.serialType === 'IMEI' ? 'numeric' : 'text'}
              autoComplete="off"
              value={line.serials[i] ?? ''}
              onChange={(e) => {
                const serials = Array.from({ length: needed }, (_, j) => line.serials[j] ?? '')
                serials[i] = e.target.value.trim()
                onChange({ serials })
              }}
              className={`${small} font-mono`}
            />
          ))}
        </div>
      )}
    </li>
  )
}
