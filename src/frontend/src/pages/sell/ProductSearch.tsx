// Find products to sell: type a name (results appear as you type) or scan a
// barcode (the scanner types the code and presses Enter, which adds it at once).
import { useQuery } from '@tanstack/react-query'
import { useRef, useState, type KeyboardEvent } from 'react'
import { CameraScanButton } from '../../components/CameraScanner'
import { inputClass } from '../../components/Field'
import { ApiError } from '../../services/api'
import type { SellableProduct } from '../../services/products'
import { findProducts } from './findProducts'
import type { PriceTier } from '../../services/sales'
import { formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'
import { useDebounced } from '../../utils/useDebounced'
import { unitPrice } from './cartMath'

type Props = { tier: PriceTier; onAdd: (product: SellableProduct, unitId?: number) => void }

export function ProductSearch({ tier, onAdd }: Props) {
  const [text, setText] = useState('')
  const input = useRef<HTMLInputElement>(null)
  const q = useDebounced(text.trim(), 250)
  const results = useQuery({
    queryKey: ['product-lookup', q],
    queryFn: () => findProducts(q),
    enabled: q.length >= 2,
    staleTime: 60_000,
  })

  const add = (product: SellableProduct, unitId?: number) => {
    onAdd(product, unitId)
    setText('')
    input.current?.focus()
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter' || !text.trim()) return
    e.preventDefault()
    void addByCode(text.trim())
  }

  /** A scanned (USB scanner or camera) or typed code: add the product if exactly one matches. */
  const addByCode = async (code: string) => {
    try {
      const found = await findProducts(code)
      if (found.length === 1) {
        // A unit barcode (e.g. a carton) adds that unit, otherwise the base unit.
        const unit = found[0].units.find((u) => u.barcode === code)
        add(found[0], unit?.id)
      } else if (found.length === 0) {
        toast.error(`Nothing found for "${code}".`)
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Search failed.')
    }
  }

  const list = q.length >= 2 ? (results.data ?? []) : []

  return (
    <div className="relative flex gap-2">
      <label htmlFor="till-search" className="sr-only">
        Search products or scan a barcode
      </label>
      <input
        id="till-search"
        ref={input}
        autoFocus
        type="search"
        autoComplete="off"
        placeholder="Search by name, or scan a barcode"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        className={`${inputClass} min-h-12 text-base`}
      />
      <CameraScanButton onCode={(code) => void addByCode(code)} />
      {q.length >= 2 && (
        <div className="absolute inset-x-0 top-full z-20 mt-1 max-h-80 overflow-y-auto rounded-xl border border-border bg-card shadow-lg">
          {results.isFetching && list.length === 0 ? (
            <p className="p-3 text-sm text-muted">Searching…</p>
          ) : results.isError ? (
            <p className="p-3 text-sm text-danger">{results.error.message}</p>
          ) : list.length === 0 ? (
            <p className="p-3 text-sm text-muted">No products match "{q}".</p>
          ) : (
            <ul role="listbox" aria-label="Products">
              {list.map((p) => (
                <li key={p.id} className="border-b border-border last:border-0">
                  <button
                    type="button"
                    role="option"
                    aria-selected={false}
                    onClick={() => add(p)}
                    className="flex min-h-12 w-full items-center justify-between gap-3 px-3 py-2 text-left hover:bg-background"
                  >
                    <span>
                      <span className="block font-medium">{p.name}</span>
                      <span className="text-xs text-muted">
                        {p.units.map((u) => u.name).join(' · ')}
                        {p.serialType !== 'NONE' && ` · ${p.serialType === 'IMEI' ? 'IMEI' : 'Serial'} needed`}
                        {' · '}
                        <span className={p.stockOnHand <= 0 ? 'text-danger' : ''}>
                          {p.stockOnHand <= 0 ? 'Out of stock' : `${p.stockOnHand} in stock`}
                        </span>
                      </span>
                    </span>
                    <span className="text-sm font-medium tabular-nums">{formatKes(unitPrice(p.units[0], tier))}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
