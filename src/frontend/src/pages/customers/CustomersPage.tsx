// Customers (owner and manager): search, see what each bought and spent, edit
// their details and whether they agreed to promotional SMS.
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { inputClass, TextField } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import {
  createCustomer,
  customerSales,
  displayPhone,
  listCustomers,
  updateCustomer,
  type Customer,
} from '../../services/customers'
import { formatDate, formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'
import { useDebounced } from '../../utils/useDebounced'

export default function CustomersPage() {
  const [search, setSearch] = useState('')
  const [optedIn, setOptedIn] = useState<'' | 'true' | 'false'>('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [viewing, setViewing] = useState<Customer | null>(null)
  const q = useDebounced(search.trim())
  const filters = { q, optedIn: optedIn === '' ? ('' as const) : optedIn === 'true', page, size: 25 }
  const customers = useQuery({
    queryKey: ['customers', filters],
    queryFn: () => listCustomers(filters),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
          <p className="text-sm text-muted">Added automatically when the till takes a phone number.</p>
        </div>
        <Button onClick={() => setEditing('new')}>+ Add customer</Button>
      </div>
      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-[1fr_auto]">
        <input
          aria-label="Search customers"
          type="search"
          placeholder="Search by name or phone"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          className={inputClass}
        />
        <select
          aria-label="Promotions"
          value={optedIn}
          onChange={(e) => {
            setOptedIn(e.target.value as '' | 'true' | 'false')
            setPage(0)
          }}
          className={inputClass}
        >
          <option value="">Everyone</option>
          <option value="true">Agreed to promotions</option>
          <option value="false">No promotions</option>
        </select>
      </div>
      {customers.isPending ? (
        <ListSkeleton label="Loading customers" />
      ) : customers.isError ? (
        <ListError what="customers" error={customers.error} onRetry={() => void customers.refetch()} />
      ) : customers.data.content.length === 0 ? (
        <ListEmpty title="No customers found">
          {q ? 'Try another search.' : 'Sell with a phone number to add one.'}
        </ListEmpty>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {customers.data.content.map((c) => (
              <li key={c.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <button type="button" onClick={() => setViewing(c)} className="min-w-0 flex-1 text-left">
                  <p className="font-medium">
                    {c.name}
                    {c.marketingOptIn && (
                      <span className="ml-2 rounded-full bg-brand-soft px-2 py-0.5 text-xs">Promotions</span>
                    )}
                  </p>
                  <p className="text-xs text-muted">
                    {displayPhone(c.phone)} · {c.visits} visit{c.visits === 1 ? '' : 's'}
                    {c.lastPurchaseAt && ` · last ${formatDate(c.lastPurchaseAt)}`}
                  </p>
                </button>
                <p className="font-semibold tabular-nums">{formatKes(c.totalSpent)}</p>
                <Button variant="ghost" onClick={() => setEditing(c)} aria-label={`Edit ${c.name}`}>
                  Edit
                </Button>
              </li>
            ))}
          </ul>
          <Pager
            page={customers.data.page}
            totalPages={customers.data.totalPages}
            total={customers.data.totalElements}
            noun="customers"
            onPage={setPage}
          />
        </>
      )}
      {editing && <CustomerDialog customer={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      {viewing && <PurchasesDialog customer={viewing} onClose={() => setViewing(null)} />}
    </div>
  )
}

function CustomerDialog({ customer, onClose }: { customer: Customer | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(customer?.name ?? '')
  const [phone, setPhone] = useState(customer ? displayPhone(customer.phone) : '')
  const [email, setEmail] = useState(customer?.email ?? '')
  const [notes, setNotes] = useState(customer?.notes ?? '')
  const [optIn, setOptIn] = useState(customer?.marketingOptIn ?? false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      const body = { name, phone, email: email || undefined, notes, marketingOptIn: optIn }
      if (customer) await updateCustomer(customer.id, body)
      else await createCustomer(body)
      await queryClient.invalidateQueries({ queryKey: ['customers'] })
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
      title={customer ? 'Edit customer' : 'Add customer'}
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
        <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
        <TextField
          label="Phone"
          inputMode="tel"
          placeholder="0712 345 678"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <TextField label="Email (optional)" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <label className="flex min-h-11 items-center gap-3 text-sm">
          <input
            type="checkbox"
            className="size-5 accent-brand"
            checked={optIn}
            onChange={(e) => setOptIn(e.target.checked)}
          />
          Agreed to receive promotional SMS
        </label>
      </div>
    </Dialog>
  )
}

function PurchasesDialog({ customer, onClose }: { customer: Customer; onClose: () => void }) {
  const sales = useQuery({ queryKey: ['customers', 'sales', customer.id], queryFn: () => customerSales(customer.id) })
  return (
    <Dialog open title={customer.name} onClose={onClose}>
      <p className="mb-3 text-sm text-muted">
        {displayPhone(customer.phone)} · spent {formatKes(customer.totalSpent)} in {customer.visits} visit
        {customer.visits === 1 ? '' : 's'}
      </p>
      {sales.isPending ? (
        <ListSkeleton label="Loading purchases" />
      ) : sales.isError ? (
        <ListError what="purchases" error={sales.error} onRetry={() => void sales.refetch()} />
      ) : sales.data.content.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted">No purchases yet.</p>
      ) : (
        <ul className="divide-y divide-border text-sm">
          {sales.data.content.map((s) => (
            <li key={s.id}>
              <Link
                to={`/sales/${s.id}/receipt`}
                className="flex min-h-11 items-center justify-between gap-3 py-2 hover:underline"
              >
                <span>
                  {s.receiptNo} · {formatDate(s.createdAt)}
                </span>
                <span className="tabular-nums">{formatKes(s.total)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  )
}
