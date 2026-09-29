// Stock levels (owner and manager): what is on the shelf, what is running low,
// its value at cost, and when stock was last counted. Adjust stock or see the
// history of any product.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager, StatCard } from '../../components/ListStates'
import { listStock, stockSummary, type StockRow } from '../../services/stock'
import { formatDate, formatKes } from '../../utils/format'
import { countIsDue } from '../../utils/stock'
import { useDebounced } from '../../utils/useDebounced'
import { AdjustDialog, HistoryDialog } from './StockDialogs'
import { StockNav } from './StockNav'

export default function StockPage() {
  const [params, setParams] = useSearchParams()
  const low = params.get('low') === '1'
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [adjusting, setAdjusting] = useState<StockRow | null>(null)
  const [history, setHistory] = useState<StockRow | null>(null)
  const q = useDebounced(search.trim())
  const filters = { q, low, page, size: 25 }
  const rows = useQuery({
    queryKey: ['stock', 'levels', filters],
    queryFn: () => listStock(filters),
    placeholderData: keepPreviousData,
  })
  const summary = useQuery({ queryKey: ['stock', 'summary'], queryFn: stockSummary })

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Stock</h1>
      <StockNav />
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard
          label="Low on stock"
          value={summary.data ? summary.data.lowStockCount : '…'}
          tone={summary.data && summary.data.lowStockCount > 0 ? 'danger' : undefined}
        />
        <StatCard label="Stock value (at cost)" value={summary.data ? formatKes(summary.data.stockValue) : '…'} />
        <StatCard
          label="Last stock count"
          value={
            summary.data ? (
              <span className={countIsDue(summary.data.lastCountAt) ? 'text-danger' : ''}>
                {summary.data.lastCountAt ? formatDate(summary.data.lastCountAt) : 'Never'}
                {countIsDue(summary.data.lastCountAt) && (
                  <Link to="/stock/count" className="ml-2 text-sm font-medium text-brand hover:underline">
                    Count due →
                  </Link>
                )}
              </span>
            ) : (
              '…'
            )
          }
        />
      </div>

      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-[1fr_auto]">
        <input
          aria-label="Search stock"
          type="search"
          placeholder="Search by name, SKU or barcode"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          className={inputClass}
        />
        <label className="flex min-h-11 items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-5 accent-brand"
            checked={low}
            onChange={(e) => {
              setParams(e.target.checked ? { low: '1' } : {})
              setPage(0)
            }}
          />
          Only low stock
        </label>
      </div>

      {rows.isPending ? (
        <ListSkeleton label="Loading stock" />
      ) : rows.isError ? (
        <ListError what="stock" error={rows.error} onRetry={() => void rows.refetch()} />
      ) : rows.data.content.length === 0 ? (
        <ListEmpty title={low ? 'Nothing is running low' : 'No products found'}>
          {low ? 'Every product is above its alert level.' : 'Add products under Products, then receive stock.'}
        </ListEmpty>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {rows.data.content.map((r) => (
              <li key={r.productId} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {r.name}
                    {r.low && (
                      <span className="ml-2 rounded-full bg-danger-soft px-2 py-0.5 text-xs text-danger">Low</span>
                    )}
                  </p>
                  <p className="text-xs text-muted">
                    {r.category ?? 'No category'}
                    {r.reorderLevel > 0 && ` · alert at ${r.reorderLevel}`}
                    {r.serialType !== 'NONE' && ' · tracked by IMEI/serial'}
                  </p>
                </div>
                <div className="text-right tabular-nums">
                  <p className={`text-lg font-semibold ${r.stockOnHand <= 0 ? 'text-danger' : ''}`}>{r.stockOnHand}</p>
                  <p className="text-xs text-muted">{formatKes(r.stockValue)}</p>
                </div>
                <div className="flex gap-1">
                  <Button variant="secondary" onClick={() => setAdjusting(r)} aria-label={`Adjust stock of ${r.name}`}>
                    Adjust
                  </Button>
                  <Button variant="ghost" onClick={() => setHistory(r)} aria-label={`Stock history of ${r.name}`}>
                    History
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <Pager
            page={rows.data.page}
            totalPages={rows.data.totalPages}
            total={rows.data.totalElements}
            noun="products"
            onPage={setPage}
          />
        </>
      )}
      {adjusting && <AdjustDialog row={adjusting} onClose={() => setAdjusting(null)} />}
      {history && <HistoryDialog row={history} onClose={() => setHistory(null)} />}
    </div>
  )
}
