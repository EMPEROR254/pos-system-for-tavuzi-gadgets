// Sales by day (cashiers see their own; owner and managers see everyone's).
// Open one to reprint its receipt or return items.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { listSales, type SaleStatus } from '../../services/sales'
import { formatDateTime, formatKes, todayInNairobi } from '../../utils/format'
import { useDebounced } from '../../utils/useDebounced'

const STATUS_LABELS: Record<SaleStatus, string> = {
  COMPLETED: 'Completed',
  PARTLY_RETURNED: 'Partly returned',
  RETURNED: 'Returned',
}

export default function SalesPage() {
  const [from, setFrom] = useState(todayInNairobi)
  const [to, setTo] = useState(todayInNairobi)
  const [receipt, setReceipt] = useState('')
  const [page, setPage] = useState(0)
  const receiptQuery = useDebounced(receipt.trim())
  const filters = { from, to, receipt: receiptQuery, page, size: 25 }
  const sales = useQuery({
    queryKey: ['sales', filters],
    queryFn: () => listSales(filters),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Sales</h1>
      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-3">
        <label className="space-y-1 text-sm">
          <span className="font-medium">From</span>
          <input
            type="date"
            value={from}
            max={to}
            onChange={(e) => {
              setFrom(e.target.value)
              setPage(0)
            }}
            className={inputClass}
          />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">To</span>
          <input
            type="date"
            value={to}
            min={from}
            onChange={(e) => {
              setTo(e.target.value)
              setPage(0)
            }}
            className={inputClass}
          />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Receipt number</span>
          <input
            type="search"
            placeholder="e.g. TG000123"
            value={receipt}
            onChange={(e) => {
              setReceipt(e.target.value)
              setPage(0)
            }}
            className={inputClass}
          />
        </label>
      </div>

      {sales.isPending ? (
        <div role="status" aria-label="Loading sales" className="space-y-2">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-xl bg-card motion-reduce:animate-none" />
          ))}
        </div>
      ) : sales.isError ? (
        <div role="alert" className="rounded-2xl border border-danger/40 bg-card p-6 text-center">
          <p className="font-medium text-danger">Could not load sales.</p>
          <p className="mt-1 text-sm text-muted">{sales.error.message}</p>
          <Button variant="secondary" className="mt-4" onClick={() => void sales.refetch()}>
            Try again
          </Button>
        </div>
      ) : sales.data.content.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center">
          <p className="font-medium">No sales in this period</p>
          <p className="mt-1 text-sm text-muted">Pick other dates, or make a sale from the Sell screen.</p>
        </div>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {sales.data.content.map((s) => (
              <li key={s.id}>
                <Link
                  to={`/sales/${s.id}/receipt`}
                  className="flex min-h-14 items-center justify-between gap-3 px-4 py-3 hover:bg-background"
                >
                  <div className="min-w-0">
                    <p className="font-medium">
                      {s.receiptNo}
                      {s.status !== 'COMPLETED' && (
                        <span className="ml-2 rounded-full bg-danger-soft px-2 py-0.5 text-xs text-danger">
                          {STATUS_LABELS[s.status]}
                        </span>
                      )}
                    </p>
                    <p className="truncate text-xs text-muted">
                      {formatDateTime(s.createdAt)} · {s.cashierName}
                      {s.customerName ? ` · ${s.customerName}` : ''}
                    </p>
                  </div>
                  <span className="font-semibold tabular-nums">{formatKes(s.total)}</span>
                </Link>
              </li>
            ))}
          </ul>
          <nav aria-label="Pages" className="flex items-center justify-between text-sm">
            <p className="text-muted">
              {sales.data.totalElements} sales · page {sales.data.page + 1} of {Math.max(sales.data.totalPages, 1)}
            </p>
            <div className="flex gap-2">
              <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <Button
                variant="secondary"
                disabled={page + 1 >= sales.data.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          </nav>
        </>
      )}
    </div>
  )
}
