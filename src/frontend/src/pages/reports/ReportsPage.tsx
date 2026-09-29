// Reports (owner and manager): pick a report and dates, read it on screen, and
// download it as PDF (or CSV for a spreadsheet). The choice lives in the URL,
// so a report can be bookmarked or opened again with the same dates.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Button } from '../../components/Button'
import { DateRangePicker } from '../../components/DateRangePicker'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, StatCard } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { exportReport, getReport, REPORTS, type DateRange, type ReportName } from '../../services/reports'
import { todayInNairobi } from '../../utils/format'
import { formatReportValue, isNegative } from '../../utils/reportFormat'
import { toast } from '../../utils/toast'
import { ReportTableView } from './ReportTableView'

const isReport = (name: string | null): name is ReportName => REPORTS.some((r) => r.name === name)

export default function ReportsPage() {
  const [params, setParams] = useSearchParams()
  const today = todayInNairobi()
  const requested = params.get('report')
  const name: ReportName = isReport(requested) ? requested : 'daily-sales'
  const range: DateRange = { from: params.get('from') ?? today, to: params.get('to') ?? today }
  const [exporting, setExporting] = useState<'pdf' | 'csv' | null>(null)

  const report = useQuery({
    queryKey: ['reports', name, range.from, range.to],
    queryFn: () => getReport(name, range),
    placeholderData: keepPreviousData,
  })

  const choose = (next: Partial<{ report: ReportName } & DateRange>) =>
    setParams(
      { report: next.report ?? name, from: next.from ?? range.from, to: next.to ?? range.to },
      { replace: true },
    )

  const download = async (format: 'pdf' | 'csv') => {
    setExporting(format)
    try {
      await exportReport(name, range, format)
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not download the report.')
    } finally {
      setExporting(null)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Reports</h1>
          <p className="text-sm text-muted">Pick a report and the dates, then download it as PDF to print or share.</p>
        </div>
        <div className="flex gap-2">
          <Button busy={exporting === 'pdf'} onClick={() => void download('pdf')}>
            Download PDF
          </Button>
          <Button variant="secondary" busy={exporting === 'csv'} onClick={() => void download('csv')}>
            CSV
          </Button>
        </div>
      </div>

      <div className="space-y-1.5 text-sm md:hidden">
        <label htmlFor="report-choice" className="block font-medium">
          Report
        </label>
        <select
          id="report-choice"
          value={name}
          onChange={(e) => choose({ report: e.target.value as ReportName })}
          className={inputClass}
        >
          {REPORTS.map((r) => (
            <option key={r.name} value={r.name}>
              {r.label}
            </option>
          ))}
        </select>
      </div>
      <nav aria-label="Reports" className="hidden flex-wrap gap-2 md:flex">
        {REPORTS.map((r) => (
          <button
            key={r.name}
            type="button"
            aria-current={r.name === name ? 'page' : undefined}
            onClick={() => choose({ report: r.name })}
            className={`min-h-11 rounded-xl px-3 text-sm font-medium transition-colors ${
              r.name === name ? 'bg-brand text-brand-foreground' : 'bg-card text-muted hover:text-foreground'
            }`}
          >
            {r.label}
          </button>
        ))}
      </nav>

      <DateRangePicker value={range} onChange={(r) => choose(r)} />

      {report.isPending ? (
        <ListSkeleton label="Loading the report" />
      ) : report.isError ? (
        <ListError what="the report" error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <section className="space-y-4" aria-busy={report.isFetching}>
          <div>
            <h2 className="text-lg font-semibold">{report.data.title}</h2>
            <p className="text-sm text-muted">{report.data.period}</p>
          </div>
          {report.data.summary.length > 0 && (
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              {report.data.summary.map((f) => (
                <StatCard
                  key={f.label}
                  label={f.label}
                  value={formatReportValue(f.value, f.kind)}
                  tone={isNegative(f.value, f.kind) ? 'danger' : undefined}
                />
              ))}
            </div>
          )}
          {report.data.rows.length === 0 ? (
            <ListEmpty title="Nothing in these dates">Try a longer date range.</ListEmpty>
          ) : (
            <ReportTableView report={report.data} />
          )}
          {report.data.note && <p className="text-xs text-muted">{report.data.note}</p>}
          {name === 'mpesa' && (
            <Link to="/reports/mpesa" className="inline-block text-sm font-medium text-brand hover:underline">
              Import an M-Pesa statement →
            </Link>
          )}
        </section>
      )}
    </div>
  )
}
