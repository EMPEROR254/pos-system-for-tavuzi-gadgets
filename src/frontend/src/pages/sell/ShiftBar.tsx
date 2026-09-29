// The cashier's shift on the till: open it with the float in the drawer, and
// close it with a count of the cash. The count is "blind" - the till does not
// show what should be in the drawer until the count is in.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import { closeShift, currentShift, openShift, type CloseResult, type CurrentShift } from '../../services/shifts'
import { formatDateTime, formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'
import { CashCount } from './CashCount'

export function ShiftBar() {
  const queryClient = useQueryClient()
  const shift = useQuery({ queryKey: ['shift', 'current'], queryFn: currentShift })
  const [opening, setOpening] = useState(false)
  const [closing, setClosing] = useState<CurrentShift | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['shift'] })

  // Offline or still loading: selling carries on without the shift bar.
  if (shift.isPending || shift.isError) return null

  return (
    <>
      {shift.data ? (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-card px-3 py-2 text-sm shadow-sm">
          <p className="text-muted">
            Shift open since {formatDateTime(shift.data.openedAt)} · float {formatKes(shift.data.openingFloat)} ·{' '}
            {shift.data.salesCount} sales
          </p>
          <Button variant="secondary" onClick={() => setClosing(shift.data)}>
            Close shift
          </Button>
        </div>
      ) : (
        <div
          role="status"
          className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-brand/40 bg-brand-soft/40 px-3 py-2 text-sm"
        >
          <p>
            <span className="font-medium">Your shift is not open.</span> Count the float in the drawer to start.
          </p>
          <Button onClick={() => setOpening(true)}>Open shift</Button>
        </div>
      )}
      <OpenShiftDialog open={opening} onClose={() => setOpening(false)} onDone={refresh} />
      {closing && <CloseShiftDialog shift={closing} onClose={() => setClosing(null)} onDone={refresh} />}
    </>
  )
}

function OpenShiftDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const [float, setFloat] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    const amount = Number(float)
    if (float.trim() === '' || !Number.isFinite(amount) || amount < 0) return setError('Type the float, e.g. 1000.')
    setBusy(true)
    try {
      await openShift(amount)
      toast.success('Shift opened.')
      setFloat('')
      onClose()
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not open the shift.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title="Open shift"
      onClose={onClose}
      footer={
        <Button busy={busy} onClick={() => void submit()}>
          Open shift
        </Button>
      }
    >
      <TextField
        label="Float in the drawer (KES)"
        inputMode="decimal"
        value={float}
        error={error}
        hint="The cash you start with, for giving change."
        onChange={(e) => {
          setFloat(e.target.value)
          setError(undefined)
        }}
      />
    </Dialog>
  )
}

function CloseShiftDialog({
  shift,
  onClose,
  onDone,
}: {
  shift: CurrentShift
  onClose: () => void
  onDone: () => void
}) {
  const [counted, setCounted] = useState<number | null>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<CloseResult | null>(null)

  const submit = async () => {
    if (counted === null) return toast.error('Count the cash first.')
    if (!window.confirm(`Close your shift with ${formatKes(counted)} counted?`)) return
    setBusy(true)
    try {
      setResult(await closeShift(shift.id, counted, note.trim() || undefined))
      onDone()
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not close the shift.')
    } finally {
      setBusy(false)
    }
  }

  if (result) return <ShiftResult result={result} onClose={onClose} />

  return (
    <Dialog
      open
      title="Close shift - count the cash"
      onClose={onClose}
      footer={
        <Button busy={busy} disabled={counted === null} onClick={() => void submit()}>
          Close shift
        </Button>
      }
    >
      <div className="space-y-4">
        <CashCount onChange={setCounted} />
        <div className="space-y-1.5 text-sm">
          <label htmlFor="shift-note" className="block font-medium">
            Note (optional)
          </label>
          <textarea
            id="shift-note"
            rows={2}
            maxLength={300}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. KES 500 paid out for airtime"
            className="block w-full rounded-xl border border-border bg-background p-3 text-base sm:text-sm"
          />
        </div>
      </div>
    </Dialog>
  )
}

function ShiftResult({ result, onClose }: { result: CloseResult; onClose: () => void }) {
  const diff = result.shift.difference ?? 0
  const rows: [string, number | null][] = [
    ['Float', result.shift.openingFloat],
    ['Cash sales', result.shift.cashSales],
    ['Cash refunds', result.shift.cashRefunds ? -result.shift.cashRefunds : 0],
    ['Expected in drawer', result.shift.expectedCash],
    ['Counted', result.shift.countedCash],
  ]
  return (
    <Dialog open title="Shift closed" onClose={onClose} footer={<Button onClick={onClose}>Done</Button>}>
      <div className="space-y-4 text-sm">
        <p
          role="status"
          className={`rounded-xl px-3 py-2 text-base font-semibold ${
            diff === 0 ? 'bg-brand-soft' : 'bg-danger-soft text-danger'
          }`}
        >
          {diff === 0
            ? 'The cash is correct.'
            : diff < 0
              ? `Short by ${formatKes(-diff)}`
              : `Over by ${formatKes(diff)}`}
        </p>
        <dl className="grid grid-cols-2 gap-y-1">
          {rows.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-muted">{label}</dt>
              <dd className="text-right tabular-nums">{formatKes(value ?? 0)}</dd>
            </div>
          ))}
        </dl>
        <p className="text-muted">
          {result.salesCount} sales · M-Pesa {formatKes(result.mpesa)} · card {formatKes(result.card)}
        </p>
      </div>
    </Dialog>
  )
}
