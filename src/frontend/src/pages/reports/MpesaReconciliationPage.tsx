// M-Pesa reconciliation (owner and manager): import the M-Pesa statement from
// the M-Pesa business portal, then see which codes typed at the till are real,
// which have the wrong amount or don't exist (typo or fake SMS), and which
// money arrived without a sale.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, StatCard } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { importStatement, reconcile, type ReconciliationResult } from '../../services/reconciliation'
import { formatDateTime, formatKes, todayInNairobi } from '../../utils/format'
import { toast } from '../../utils/toast'

const RESULT: Record<ReconciliationResult, { label: string; tone: string; help: string }> = {
  MATCHED: { label: 'Matched', tone: 'bg-brand-soft', help: 'The money arrived.' },
  AMOUNT_DIFFERS: {
    label: 'Amount differs',
    tone: 'bg-danger-soft text-danger',
    help: 'The code is real but the amount is not what the till recorded.',
  },
  NOT_IN_STATEMENT: {
    label: 'Not in statement',
    tone: 'bg-danger-soft text-danger',
    help: 'No such money received - a typo, or a fake SMS.',
  },
  NOT_IN_SYSTEM: {
    label: 'No sale',
    tone: 'bg-background text-muted',
    help: 'Money arrived but no sale used this code.',
  },
}

/** Reads a picked file as text (FileReader works in every browser the tills use). */
function readText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('Could not read that file.'))
    reader.readAsText(file)
  })
}

export default function MpesaReconciliationPage() {
  const queryClient = useQueryClient()
  const file = useRef<HTMLInputElement>(null)
  const [from, setFrom] = useState(todayInNairobi)
  const [to, setTo] = useState(todayInNairobi)
  const [importing, setImporting] = useState(false)
  const report = useQuery({ queryKey: ['mpesa-reconciliation', from, to], queryFn: () => reconcile(from, to) })

  const upload = async (picked: File | undefined) => {
    if (!picked) return
    setImporting(true)
    try {
      const result = await importStatement(await readText(picked))
      toast.success(
        `Statement imported: ${result.added} new payment${result.added === 1 ? '' : 's'}` +
          (result.alreadyImported > 0 ? `, ${result.alreadyImported} already there.` : '.'),
      )
      await queryClient.invalidateQueries({ queryKey: ['mpesa-reconciliation'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not read that file.')
    } finally {
      setImporting(false)
      if (file.current) file.current.value = ''
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">M-Pesa reconciliation</h1>
          <p className="text-sm text-muted">
            Download the statement (CSV) from the M-Pesa business portal for your Till and Paybill, then import it.
          </p>
        </div>
        <div>
          <input
            ref={file}
            id="statement-file"
            type="file"
            accept=".csv,text/csv"
            className="sr-only"
            onChange={(e) => void upload(e.target.files?.[0])}
          />
          <Button busy={importing} onClick={() => file.current?.click()}>
            Import M-Pesa statement
          </Button>
        </div>
      </div>

      <div className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm sm:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span className="font-medium">From</span>
          <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className={inputClass} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">To</span>
          <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className={inputClass} />
        </label>
      </div>

      {report.isPending ? (
        <ListSkeleton label="Checking M-Pesa payments" />
      ) : report.isError ? (
        <ListError what="the reconciliation" error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <>
          {!report.data.statementImported && report.data.lines.length > 0 && (
            <p role="alert" className="rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">
              No statement imported for these dates yet - every code shows as “Not in statement” until you import it.
            </p>
          )}
          <div className="grid gap-3 sm:grid-cols-4">
            <StatCard label="Typed at the till" value={formatKes(report.data.systemTotal)} />
            <StatCard label="In the statement" value={formatKes(report.data.statementTotal)} />
            <StatCard label="Matched" value={report.data.matched} />
            <StatCard
              label="To check"
              value={report.data.problems}
              tone={report.data.problems > 0 ? 'danger' : undefined}
            />
          </div>
          {report.data.lines.length === 0 ? (
            <ListEmpty title="No M-Pesa payments for these dates" />
          ) : (
            <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
              {report.data.lines.map((l) => (
                <li key={`${l.result}-${l.code}`} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                  <div className="min-w-0 flex-1">
                    <p className="font-mono font-medium">{l.code}</p>
                    <p className="text-xs text-muted">
                      {formatDateTime(l.time)}
                      {l.cashierName && ` · ${l.cashierName}`}
                      {l.otherParty && ` · ${l.otherParty}`}
                      {' · '}
                      {RESULT[l.result].help}
                    </p>
                  </div>
                  <div className="text-right tabular-nums">
                    {l.systemAmount != null && <p>Till: {formatKes(l.systemAmount)}</p>}
                    {l.statementAmount != null && <p className="text-muted">M-Pesa: {formatKes(l.statementAmount)}</p>}
                  </div>
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${RESULT[l.result].tone}`}>
                    {RESULT[l.result].label}
                  </span>
                  {l.saleId && (
                    <Link to={`/sales/${l.saleId}/receipt`} className="text-sm font-medium text-brand hover:underline">
                      {l.receiptNo}
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
