// The receipt for a sale saved on this till while offline. It can be printed
// from the browser now; the receipt number and KRA eTIMS invoice come when the
// sale reaches the server (then the full receipt is under Sales).
import { Link, useParams } from 'react-router'
import { Button } from '../../components/Button'
import { useOffline } from '../../services/offline'
import { formatDateTime, formatKes } from '../../utils/format'

const money = (n: number) => formatKes(n).replace('KES ', '')

export default function OfflineReceiptPage() {
  const clientRef = useParams().clientRef ?? ''
  const sale = useOffline((s) => s.waiting.find((w) => w.clientRef === clientRef))

  if (!sale) {
    return (
      <section className="mx-auto max-w-sm rounded-2xl border border-border bg-card p-6 text-center shadow-sm">
        <p className="font-medium">This sale has reached the server.</p>
        <p className="mt-1 text-sm text-muted">
          Find its full receipt, with the receipt number and KRA eTIMS details, under Sales.
        </p>
        <Link to="/sales" className="mt-4 inline-flex min-h-11 items-center font-medium text-brand hover:underline">
          Go to Sales
        </Link>
      </section>
    )
  }

  return (
    <div className="space-y-4">
      <div className="mx-auto flex max-w-sm gap-2 print:hidden">
        <Link
          to="/sell"
          className="inline-flex min-h-11 flex-1 items-center justify-center rounded-xl bg-brand px-4 text-sm font-medium text-brand-foreground shadow-sm hover:opacity-90"
        >
          Next sale
        </Link>
        <Button variant="secondary" className="flex-1" onClick={() => window.print()}>
          Print
        </Button>
      </div>
      <article
        aria-label="Offline receipt"
        className="mx-auto w-full max-w-sm rounded-2xl border border-border bg-card p-5 font-mono text-[13px] leading-snug shadow-sm print:max-w-[72mm] print:border-0 print:p-0 print:text-black"
      >
        <h2 className="text-center text-lg font-bold">TAVUZI GADGETS</h2>
        <p className="text-center">Sale recorded offline</p>
        <hr className="my-2 border-dashed border-current opacity-40" />
        <p>{formatDateTime(sale.createdAt)}</p>
        <p>Served by {sale.cashierName}</p>
        <hr className="my-2 border-dashed border-current opacity-40" />
        <ul className="space-y-1">
          {sale.receipt.lines.map((l, i) => (
            <li key={i}>
              <p>{l.name}</p>
              <p className="flex justify-between pl-3 tabular-nums">
                <span>
                  {l.quantity} {l.unitName}
                </span>
                <span>{money(l.total)}</span>
              </p>
            </li>
          ))}
        </ul>
        <hr className="my-2 border-dashed border-current opacity-40" />
        <p className="flex justify-between text-base font-bold tabular-nums">
          <span>TOTAL KES</span>
          <span>{money(sale.total)}</span>
        </p>
        {sale.receipt.payments.map((p, i) => (
          <p key={i} className="flex justify-between tabular-nums">
            <span>
              {p.label}
              {p.reference ? ` ${p.reference}` : ''}
            </span>
            <span>{money(p.amount)}</span>
          </p>
        ))}
        {sale.receipt.change > 0 && (
          <p className="flex justify-between tabular-nums">
            <span>Change</span>
            <span>{money(sale.receipt.change)}</span>
          </p>
        )}
        <hr className="my-2 border-dashed border-current opacity-40" />
        <p className="text-center">
          Receipt number and KRA eTIMS invoice will follow when the till reconnects. Ask for an SMS or reprint then.
        </p>
      </article>
    </div>
  )
}
