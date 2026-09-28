// Warranty check at the counter: type or scan an IMEI, serial or receipt number.
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { inputClass } from '../components/Field'
import { lookupWarranty, type WarrantyResult } from '../services/warranty'
import { formatDate } from '../utils/format'
import { useDebounced } from '../utils/useDebounced'

const STATUS: Record<WarrantyResult['status'], { label: string; tone: string }> = {
  ACTIVE: { label: 'Under warranty', tone: 'bg-brand-soft text-foreground' },
  EXPIRED: { label: 'Warranty ended', tone: 'bg-danger-soft text-danger' },
  NO_WARRANTY: { label: 'No warranty', tone: 'bg-background text-muted' },
  RETURNED: { label: 'Returned', tone: 'bg-background text-muted' },
}

export default function WarrantyPage() {
  const [text, setText] = useState('')
  const q = useDebounced(text.trim(), 300)
  const results = useQuery({ queryKey: ['warranty', q], queryFn: () => lookupWarranty(q), enabled: q.length >= 3 })

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Warranty check</h1>
        <p className="text-sm text-muted">Find a sold item by IMEI, serial number or receipt number.</p>
      </div>
      <label htmlFor="warranty-search" className="sr-only">
        IMEI, serial or receipt number
      </label>
      <input
        id="warranty-search"
        type="search"
        autoFocus
        placeholder="IMEI, serial or receipt number"
        value={text}
        onChange={(e) => setText(e.target.value)}
        className={`${inputClass} min-h-12 font-mono`}
      />
      {q.length < 3 ? null : results.isPending ? (
        <div
          role="status"
          aria-label="Searching"
          className="h-24 animate-pulse rounded-2xl bg-card motion-reduce:animate-none"
        />
      ) : results.isError ? (
        <p role="alert" className="rounded-xl bg-danger-soft p-3 text-sm text-danger">
          {results.error.message}
        </p>
      ) : results.data.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-8 text-center text-sm text-muted">
          Nothing sold matches "{q}".
        </div>
      ) : (
        <ul className="space-y-3">
          {results.data.map((r, i) => (
            <li
              key={`${r.receiptNo}-${r.serial ?? i}`}
              className="rounded-2xl border border-border bg-card p-4 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-medium">{r.productName}</p>
                  {r.serial && <p className="font-mono text-sm">{r.serial}</p>}
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS[r.status].tone}`}>
                  {STATUS[r.status].label}
                </span>
              </div>
              <p className="mt-2 text-sm text-muted">
                Sold {formatDate(r.soldAt)} on{' '}
                <Link to={`/sales/${r.saleId}/receipt`} className="font-medium text-brand hover:underline">
                  {r.receiptNo}
                </Link>
                {r.warrantyEndsAt && ` · warranty until ${formatDate(r.warrantyEndsAt)}`}
                {r.customerName && ` · ${r.customerName}`}
                {r.customerPhone && ` (${r.customerPhone})`}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
