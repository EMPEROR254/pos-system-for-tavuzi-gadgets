// KRA eTIMS (owner and manager): every sale and return becomes an eTIMS
// invoice. See what reached KRA, what is waiting (e.g. internet down - it goes
// by itself later) and what KRA refused, with the reason and a "send again".
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager, StatCard } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { etimsSummary, listEtimsInvoices, retryEtimsInvoice } from '../../services/etims'
import { formatDateTime } from '../../utils/format'
import { toast } from '../../utils/toast'

const MODE_LABEL = { OFF: 'Not connected yet', SANDBOX: 'KRA test system', PRODUCTION: 'Live with KRA' }
const STATUS = {
  SENT: { label: 'At KRA', tone: 'bg-brand-soft' },
  PENDING: { label: 'Waiting', tone: 'bg-background text-muted' },
  REJECTED: { label: 'Refused', tone: 'bg-danger-soft text-danger' },
}

export default function EtimsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const summary = useQuery({ queryKey: ['etims', 'summary'], queryFn: etimsSummary })
  const invoices = useQuery({
    queryKey: ['etims', 'invoices', status, page],
    queryFn: () => listEtimsInvoices({ status, page }),
    placeholderData: keepPreviousData,
  })

  const retry = async (id: number) => {
    try {
      await retryEtimsInvoice(id)
      toast.success('The invoice will be sent to KRA again shortly.')
      await queryClient.invalidateQueries({ queryKey: ['etims'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not do that.')
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">KRA eTIMS</h1>
        <p className="text-sm text-muted">
          Invoices go to KRA in the background. If the internet is down they wait and go when it is back.
        </p>
      </div>
      {summary.data?.mode === 'OFF' && (
        <p role="status" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
          eTIMS is not connected to KRA yet: invoices are kept and will all be sent once the shop's eTIMS device is
          registered and set up (see docs/ETIMS.md).
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-4">
        <StatCard label="Connection" value={summary.data ? MODE_LABEL[summary.data.mode] : '…'} />
        <StatCard label="At KRA" value={summary.data?.sent ?? '…'} />
        <StatCard label="Waiting" value={summary.data?.pending ?? '…'} />
        <StatCard
          label="Refused by KRA"
          value={summary.data?.rejected ?? '…'}
          tone={summary.data && summary.data.rejected > 0 ? 'danger' : undefined}
        />
      </div>
      <select
        aria-label="Show"
        value={status}
        onChange={(e) => {
          setStatus(e.target.value)
          setPage(0)
        }}
        className={`${inputClass} sm:w-60`}
      >
        <option value="">All invoices</option>
        <option value="REJECTED">Refused by KRA</option>
        <option value="PENDING">Waiting</option>
        <option value="SENT">At KRA</option>
      </select>
      {invoices.isPending ? (
        <ListSkeleton label="Loading invoices" />
      ) : invoices.isError ? (
        <ListError what="invoices" error={invoices.error} onRetry={() => void invoices.refetch()} />
      ) : invoices.data.content.length === 0 ? (
        <ListEmpty title="No invoices here" />
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {invoices.data.content.map((i) => (
              <li key={i.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    Invoice {i.invoiceNo} · {i.kind === 'SALE' ? 'Sale' : 'Credit note (return)'} ·{' '}
                    <Link to={`/sales/${i.saleId}/receipt`} className="text-brand hover:underline">
                      {i.receiptNo}
                    </Link>
                  </p>
                  <p className="text-xs text-muted">
                    {formatDateTime(i.createdAt)}
                    {i.sentAt && ` · at KRA ${formatDateTime(i.sentAt)}`}
                    {i.attempts > 0 && i.status !== 'SENT' && ` · ${i.attempts} tries`}
                  </p>
                  {i.lastError && i.status !== 'SENT' && <p className="text-xs text-danger">{i.lastError}</p>}
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS[i.status].tone}`}>
                  {STATUS[i.status].label}
                </span>
                {i.status === 'REJECTED' && (
                  <Button variant="secondary" onClick={() => void retry(i.id)}>
                    Send again
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <Pager
            page={invoices.data.page}
            totalPages={invoices.data.totalPages}
            total={invoices.data.totalElements}
            noun="invoices"
            onPage={setPage}
          />
        </>
      )}
    </div>
  )
}
