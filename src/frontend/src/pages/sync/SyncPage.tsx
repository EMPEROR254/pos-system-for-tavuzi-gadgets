// Sync: sales saved on this till while it couldn't reach the server, and what
// the server still has waiting for the internet (KRA eTIMS invoices, SMS).
// Refused sales stay here until a manager sorts them out - they are never lost.
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { ListEmpty, StatCard } from '../../components/ListStates'
import { retryWaitingSale, syncWaitingSales, useOffline } from '../../services/offline'
import { useSession } from '../../services/session'
import { serverWaiting } from '../../services/sync'
import { formatDateTime, formatKes } from '../../utils/format'
import { can } from '../../utils/permissions'
import { toast } from '../../utils/toast'

export default function SyncPage() {
  const user = useSession((s) => s.user)
  const { waiting, syncing, catalogue, remove } = useOffline()
  const server = useQuery({ queryKey: ['sync', 'status'], queryFn: serverWaiting, refetchInterval: 30_000 })
  const [busy, setBusy] = useState<string | null>(null)
  const manager = can(user, 'VIEW_REPORTS')

  const sendAll = async () => {
    const synced = await syncWaitingSales()
    const left = useOffline.getState().waiting
    if (synced.length > 0) toast.success(`${synced.length} sale${synced.length === 1 ? '' : 's'} sent.`)
    else if (left.some((w) => !w.problem)) toast.error('Still no connection to the server. Sales stay safe here.')
  }

  const retry = async (clientRef: string) => {
    setBusy(clientRef)
    try {
      if (await retryWaitingSale(clientRef)) toast.success('Sale sent.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Sync</h1>
      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard
          label="Sales waiting on this till"
          value={waiting.length}
          tone={waiting.some((w) => w.problem) ? 'danger' : undefined}
        />
        <StatCard
          label="KRA eTIMS invoices waiting"
          value={
            server.data ? `${server.data.etimsPending}${server.data.etimsMode === 'OFF' ? ' (eTIMS off)' : ''}` : '…'
          }
        />
        <StatCard label="SMS waiting" value={server.data ? server.data.smsQueued : '…'} />
      </div>
      <p className="text-sm text-muted">
        {catalogue
          ? `This till has a copy of ${catalogue.products.length} products (updated ${formatDateTime(catalogue.generatedAt)}) for selling offline.`
          : 'This till has no offline copy of the products yet - it downloads one while connected.'}
      </p>

      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Sales saved on this till</h2>
        <Button variant="secondary" busy={syncing} disabled={waiting.length === 0} onClick={() => void sendAll()}>
          Send now
        </Button>
      </div>
      {waiting.length === 0 ? (
        <ListEmpty title="Nothing waiting">Every sale from this till has reached the server.</ListEmpty>
      ) : (
        <ul className="space-y-3">
          {waiting.map((w) => (
            <li
              key={w.clientRef}
              className={`space-y-2 rounded-2xl border bg-card p-4 shadow-sm ${w.problem ? 'border-danger/50' : 'border-border'}`}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-medium">
                    {formatDateTime(w.createdAt)} · {w.itemCount} item{w.itemCount === 1 ? '' : 's'}
                  </p>
                  <p className="text-xs text-muted">Sold by {w.cashierName}</p>
                </div>
                <p className="font-semibold tabular-nums">{formatKes(w.total)}</p>
              </div>
              {w.problem ? (
                <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
                  The server refused this sale: {w.problem}
                </p>
              ) : (
                <p className="text-sm text-muted">Waiting for the connection.</p>
              )}
              <div className="flex flex-wrap gap-2">
                <Link
                  to={`/sync/receipt/${w.clientRef}`}
                  className="inline-flex min-h-11 items-center rounded-xl border border-border px-4 text-sm font-medium hover:bg-background"
                >
                  Receipt
                </Link>
                {w.problem &&
                  (manager ? (
                    <>
                      <Button busy={busy === w.clientRef} onClick={() => void retry(w.clientRef)}>
                        Try again
                      </Button>
                      <Button
                        variant="ghost"
                        className="text-danger"
                        onClick={() => {
                          if (window.confirm('Remove this sale from the till? It will not be recorded.'))
                            remove(w.clientRef)
                        }}
                      >
                        Remove
                      </Button>
                    </>
                  ) : (
                    <p className="self-center text-sm text-muted">Ask a manager to sort this out.</p>
                  ))}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
