// The receipt as shown on screen and printed from the browser. Narrow (like
// till paper) and plain black-on-white when printed, whatever the theme.
import { QrCode } from '../../components/QrCode'
import type { EtimsReceipt, Receipt } from '../../services/sales'
import { formatDate, formatDateTime, formatKes } from '../../utils/format'

const money = (n: number) => formatKes(n).replace('KES ', '')

export function ReceiptView({ receipt }: { receipt: Receipt }) {
  const sale = receipt.sale
  return (
    <article
      aria-label={`Receipt ${sale.receiptNo}`}
      className="mx-auto w-full max-w-sm rounded-2xl border border-border bg-card p-5 font-mono text-[13px] leading-snug shadow-sm print:max-w-[72mm] print:rounded-none print:border-0 print:bg-white print:p-0 print:text-black print:shadow-none"
    >
      <header className="text-center">
        <h2 className="text-lg font-bold">{receipt.shopName}</h2>
        {receipt.shopDetails.map((d) => (
          <p key={d}>{d}</p>
        ))}
      </header>
      <hr className="my-2 border-dashed border-current opacity-40" />
      <p className="flex justify-between">
        <span>Receipt {sale.receiptNo}</span>
        <span>{formatDateTime(sale.createdAt)}</span>
      </p>
      <p>Served by {sale.cashierName}</p>
      {sale.customerName && <p>Customer: {sale.customerName}</p>}
      {sale.priceTier === 'WHOLESALE' && <p>Wholesale prices</p>}
      {sale.status !== 'COMPLETED' && (
        <p className="font-bold">{sale.status === 'RETURNED' ? 'ALL ITEMS RETURNED' : 'SOME ITEMS RETURNED'}</p>
      )}
      <hr className="my-2 border-dashed border-current opacity-40" />
      <ul className="space-y-1.5">
        {sale.lines.map((l) => (
          <li key={l.id}>
            <p>
              {l.productName} <span className="opacity-70">[{l.vatCode}]</span>
            </p>
            <p className="flex justify-between pl-3 tabular-nums">
              <span>
                {l.quantity} {l.unitName} × {money(l.unitPrice)}
              </span>
              <span>{money(l.lineTotal + l.discount)}</span>
            </p>
            {l.discount > 0 && (
              <p className="flex justify-between pl-3 tabular-nums">
                <span>Discount</span>
                <span>−{money(l.discount)}</span>
              </p>
            )}
            {l.serials.map((s) => (
              <p key={s.serial} className="pl-3">
                IMEI/SN: {s.serial}
                {s.returned && ' (returned)'}
              </p>
            ))}
            {l.warrantyEndsAt && <p className="pl-3">Warranty until {formatDate(l.warrantyEndsAt)}</p>}
            {l.returnedQuantity > 0 && <p className="pl-3">Returned: {l.returnedQuantity}</p>}
          </li>
        ))}
      </ul>
      <hr className="my-2 border-dashed border-current opacity-40" />
      <dl className="tabular-nums">
        {sale.discountTotal > 0 && (
          <>
            <Row label="Subtotal" value={money(sale.subtotal)} />
            <Row label="Discount" value={`−${money(sale.discountTotal)}`} />
          </>
        )}
        <Row label="TOTAL KES" value={money(sale.total)} bold />
        {receipt.vatBreakdown.map((v) => (
          <Row key={v.code} label={`${v.code} VAT ${v.ratePercent}% on ${money(v.taxable)}`} value={money(v.vat)} />
        ))}
      </dl>
      <hr className="my-2 border-dashed border-current opacity-40" />
      <dl className="tabular-nums">
        {sale.payments.map((p, i) => (
          <Row key={i} label={`${p.label}${p.reference ? ` ${p.reference}` : ''}`} value={money(p.amount)} />
        ))}
        {sale.changeDue > 0 && <Row label="Change" value={money(sale.changeDue)} />}
      </dl>
      <hr className="my-2 border-dashed border-current opacity-40" />
      {receipt.etims && <EtimsBlock etims={receipt.etims} />}
      <footer className="space-y-1 text-center">
        {receipt.paymentInstructions.map((line) => (
          <p key={line} className="font-bold">
            {line}
          </p>
        ))}
        {receipt.returnPolicy && <p>{receipt.returnPolicy}</p>}
        {receipt.footer.map((line) => (
          <p key={line}>{line}</p>
        ))}
      </footer>
    </article>
  )
}

function Row({ label, value, bold = false }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between gap-3 ${bold ? 'text-base font-bold' : ''}`}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

/** KRA eTIMS: the signed invoice details and a QR code to check it with KRA. */
function EtimsBlock({ etims }: { etims: EtimsReceipt }) {
  if (etims.status !== 'SENT') {
    return (
      <>
        <p className="text-center">KRA eTIMS invoice {etims.invoiceNo}: being sent to KRA</p>
        <hr className="my-2 border-dashed border-current opacity-40" />
      </>
    )
  }
  return (
    <>
      <p className="text-center font-bold">KRA eTIMS</p>
      <dl className="tabular-nums">
        <Row label="Invoice no." value={String(etims.invoiceNo)} />
        <Row label="SCU ID" value={etims.scuId ?? ''} />
        <Row label="Receipt no." value={`${etims.scuReceiptNo}/${etims.totalReceiptNo}`} />
        <Row label="Internal data" value={etims.internalData ?? ''} />
        <Row label="Signature" value={etims.signature ?? ''} />
      </dl>
      {etims.verifyUrl && (
        <div className="mt-2">
          <QrCode value={etims.verifyUrl} label="QR code to check this invoice with KRA" />
        </div>
      )}
      <hr className="my-2 border-dashed border-current opacity-40" />
    </>
  )
}
