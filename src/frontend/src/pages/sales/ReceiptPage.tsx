// A sale's receipt: print it (browser or thermal printer), start the next sale,
// or return items (owner-approved).
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '../../components/Button'
import { ApiError } from '../../services/api'
import { getReceipt, printReceipt, smsReceipt } from '../../services/sales'
import { toast } from '../../utils/toast'
import { ReceiptView } from './ReceiptView'
import { ReturnDialog } from './ReturnDialog'

export default function ReceiptPage() {
  const id = Number(useParams().id)
  const [params] = useSearchParams()
  const justSold = params.get('new') === '1'
  const [returning, setReturning] = useState(false)
  const [printing, setPrinting] = useState(false)
  const [texting, setTexting] = useState(false)

  const sendSms = async () => {
    setTexting(true)
    try {
      await smsReceipt(id)
      toast.success('SMS receipt on its way.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not send the SMS.')
    } finally {
      setTexting(false)
    }
  }
  const receipt = useQuery({ queryKey: ['receipt', id], queryFn: () => getReceipt(id), enabled: Number.isFinite(id) })

  const printToPrinter = async () => {
    setPrinting(true)
    try {
      await printReceipt(id)
      toast.success('Sent to the receipt printer.')
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not print.')
    } finally {
      setPrinting(false)
    }
  }

  if (receipt.isPending) {
    return (
      <div
        role="status"
        aria-label="Loading receipt"
        className="mx-auto h-96 max-w-sm animate-pulse rounded-2xl bg-card"
      />
    )
  }
  if (receipt.isError) {
    return (
      <div role="alert" className="mx-auto max-w-sm rounded-2xl border border-danger/40 bg-card p-6 text-center">
        <p className="font-medium text-danger">Could not load the receipt.</p>
        <p className="mt-1 text-sm text-muted">{receipt.error.message}</p>
        <Button variant="secondary" className="mt-4" onClick={() => void receipt.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  const sale = receipt.data.sale
  return (
    <div className="space-y-4">
      <div className="mx-auto flex max-w-sm flex-wrap gap-2 print:hidden">
        {justSold && (
          <Link
            to="/sell"
            className="inline-flex min-h-11 flex-1 items-center justify-center rounded-xl bg-brand px-4 text-sm font-medium text-brand-foreground shadow-sm hover:opacity-90"
          >
            Next sale
          </Link>
        )}
        <Button variant="secondary" className="flex-1" onClick={() => window.print()}>
          Print
        </Button>
        <Button variant="secondary" className="flex-1" busy={printing} onClick={() => void printToPrinter()}>
          Receipt printer
        </Button>
        {sale.customerPhone && (
          <Button variant="secondary" className="flex-1" busy={texting} onClick={() => void sendSms()}>
            Send SMS
          </Button>
        )}
        {!justSold && sale.status !== 'RETURNED' && (
          <Button variant="ghost" className="w-full text-danger" onClick={() => setReturning(true)}>
            Return items…
          </Button>
        )}
      </div>
      <ReceiptView receipt={receipt.data} />
      {returning && <ReturnDialog sale={sale} open onClose={() => setReturning(false)} />}
    </div>
  )
}
