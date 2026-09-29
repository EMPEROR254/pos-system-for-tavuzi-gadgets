// Suppliers and what the shop owes them, plus recent deliveries. Receive a new
// delivery, pay a supplier, or open their statement.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager, StatCard } from '../../components/ListStates'
import { listDeliveries, listSuppliers, type Supplier } from '../../services/purchases'
import { formatDate, formatKes } from '../../utils/format'
import { useDebounced } from '../../utils/useDebounced'
import { StockNav } from './StockNav'
import { PayDialog, StatementDialog, SupplierDialog } from './SupplierDialogs'

export default function SuppliersPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null)
  const [paying, setPaying] = useState<Supplier | null>(null)
  const [statement, setStatement] = useState<Supplier | null>(null)
  const q = useDebounced(search.trim())
  const filters = { q, page, size: 25 }
  const suppliers = useQuery({
    queryKey: ['suppliers', filters],
    queryFn: () => listSuppliers(filters),
    placeholderData: keepPreviousData,
  })
  const deliveries = useQuery({ queryKey: ['suppliers', 'deliveries'], queryFn: () => listDeliveries({ size: 5 }) })
  const owed = (suppliers.data?.content ?? []).reduce((sum, s) => sum + s.balance, 0)

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Stock</h1>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setEditing('new')}>
            + Supplier
          </Button>
          <Link
            to="/stock/deliveries/new"
            className="inline-flex min-h-11 items-center rounded-xl bg-brand px-4 text-sm font-medium text-brand-foreground shadow-sm hover:opacity-90"
          >
            Receive delivery
          </Link>
        </div>
      </div>
      <StockNav />
      <div className="grid gap-3 sm:grid-cols-2">
        <StatCard
          label="Owed to suppliers on this page"
          value={formatKes(owed)}
          tone={owed > 0 ? 'danger' : undefined}
        />
        <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
          <p className="text-sm text-muted">Latest deliveries</p>
          <ul className="mt-1 space-y-1 text-sm">
            {(deliveries.data?.content ?? []).map((d) => (
              <li key={d.id} className="flex justify-between gap-2">
                <span className="truncate">
                  {formatDate(d.receivedAt)} · {d.supplierName}
                  {d.invoiceNo && ` · ${d.invoiceNo}`}
                </span>
                <span className="tabular-nums">{formatKes(d.total)}</span>
              </li>
            ))}
            {deliveries.data?.content.length === 0 && <li className="text-muted">None yet.</li>}
          </ul>
        </div>
      </div>
      <input
        aria-label="Search suppliers"
        type="search"
        placeholder="Search suppliers"
        value={search}
        onChange={(e) => {
          setSearch(e.target.value)
          setPage(0)
        }}
        className={inputClass}
      />
      {suppliers.isPending ? (
        <ListSkeleton label="Loading suppliers" />
      ) : suppliers.isError ? (
        <ListError what="suppliers" error={suppliers.error} onRetry={() => void suppliers.refetch()} />
      ) : suppliers.data.content.length === 0 ? (
        <ListEmpty title="No suppliers yet">Add the people and shops you buy stock from.</ListEmpty>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {suppliers.data.content.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{s.name}</p>
                  <p className="text-xs text-muted">
                    Bought {formatKes(s.purchased)} · paid {formatKes(s.paid)}
                    {s.phone && ` · ${s.phone}`}
                  </p>
                </div>
                <p className={`font-semibold tabular-nums ${s.balance > 0 ? 'text-danger' : ''}`}>
                  {s.balance > 0 ? `Owe ${formatKes(s.balance)}` : 'Nothing owed'}
                </p>
                <div className="flex gap-1">
                  <Button variant="secondary" disabled={s.balance <= 0} onClick={() => setPaying(s)}>
                    Pay
                  </Button>
                  <Button variant="ghost" onClick={() => setStatement(s)}>
                    Statement
                  </Button>
                  <Button variant="ghost" onClick={() => setEditing(s)} aria-label={`Edit ${s.name}`}>
                    Edit
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <Pager
            page={suppliers.data.page}
            totalPages={suppliers.data.totalPages}
            total={suppliers.data.totalElements}
            noun="suppliers"
            onPage={setPage}
          />
        </>
      )}
      {editing && <SupplierDialog supplier={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {paying && <PayDialog supplier={paying} onClose={() => setPaying(null)} />}
      {statement && <StatementDialog supplier={statement} onClose={() => setStatement(null)} />}
    </div>
  )
}
