// A report's rows: a table with a totals row on laptops, stacked cards on phones.
import type { ReportTable } from '../../services/reports'
import { formatReportValue, isNegative, isNumeric } from '../../utils/reportFormat'

export function ReportTableView({ report }: { report: ReportTable }) {
  const [first, ...rest] = report.columns
  const cell = (row: Record<string, unknown>, key: string) => row[key] as string | number | null

  return (
    <>
      {/* Phones: one card per row, the first column as the heading. */}
      <ul className="space-y-2 md:hidden" aria-label={report.title}>
        {report.rows.map((row, i) => (
          <li key={i} className="rounded-2xl border border-border bg-card p-4 text-sm shadow-sm">
            <p className="font-medium">{formatReportValue(row[first.key], first.kind)}</p>
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1">
              {rest.map((c) => (
                <div key={c.key} className="contents">
                  <dt className="text-muted">{c.label}</dt>
                  <dd
                    className={`${isNumeric(c.kind) ? 'text-right tabular-nums' : ''} ${
                      isNegative(cell(row, c.key), c.kind) ? 'text-danger' : ''
                    }`}
                  >
                    {formatReportValue(row[c.key], c.kind)}
                  </dd>
                </div>
              ))}
            </dl>
          </li>
        ))}
        {report.totals && (
          <li className="rounded-2xl border border-brand/40 bg-brand-soft/40 p-4 text-sm font-semibold">
            <p>Total</p>
            <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1">
              {rest
                .filter((c) => report.totals![c.key] !== undefined)
                .map((c) => (
                  <div key={c.key} className="contents">
                    <dt>{c.label}</dt>
                    <dd className="text-right tabular-nums">{formatReportValue(report.totals![c.key], c.kind)}</dd>
                  </div>
                ))}
            </dl>
          </li>
        )}
      </ul>

      {/* Laptops: a table that scrolls sideways if it is wider than the screen. */}
      <div className="hidden overflow-x-auto rounded-2xl border border-border bg-card shadow-sm md:block">
        <table className="w-full text-sm">
          <caption className="sr-only">{report.title}</caption>
          <thead className="bg-background text-left text-muted">
            <tr>
              {report.columns.map((c) => (
                <th
                  key={c.key}
                  scope="col"
                  className={`px-3 py-2 font-medium whitespace-nowrap ${isNumeric(c.kind) ? 'text-right' : ''}`}
                >
                  {c.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {report.rows.map((row, i) => (
              <tr key={i}>
                {report.columns.map((c) => (
                  <td
                    key={c.key}
                    className={`px-3 py-2 ${isNumeric(c.kind) ? 'text-right tabular-nums whitespace-nowrap' : ''} ${
                      isNegative(cell(row, c.key), c.kind) ? 'text-danger' : ''
                    }`}
                  >
                    {formatReportValue(row[c.key], c.kind)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
          {report.totals && (
            <tfoot className="border-t-2 border-border bg-background font-semibold">
              <tr>
                {report.columns.map((c, i) => (
                  <td key={c.key} className={`px-3 py-2 ${isNumeric(c.kind) ? 'text-right tabular-nums' : ''}`}>
                    {i === 0 ? 'Total' : formatReportValue(report.totals![c.key], c.kind)}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </>
  )
}
