// The owner's dashboard: key numbers for the chosen dates, charts, and links to
// the full reports. Charts are loaded with this screen only, never on the till.
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { DateRangePicker } from '../../components/DateRangePicker'
import { ListError, StatCard } from '../../components/ListStates'
import { getDashboard, type DateRange } from '../../services/reports'
import { formatKes } from '../../utils/format'
import { quickRanges } from '../../utils/dateRanges'
import { PaymentMix, SalesTrend, TopBars } from './DashboardCharts'

export default function DashboardPage() {
  // Opens on "This week" so the trend chart has something to show.
  const week = quickRanges().find((r) => r.label === 'This week')!
  const [range, setRange] = useState<DateRange>({ from: week.from, to: week.to })
  const dashboard = useQuery({
    queryKey: ['reports', 'dashboard', range.from, range.to],
    queryFn: () => getDashboard(range),
    placeholderData: keepPreviousData,
  })

  const report = (name: string) => `/reports?report=${name}&from=${range.from}&to=${range.to}`

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <Link to={report('daily-sales')} className="text-sm font-medium text-brand hover:underline">
          All reports →
        </Link>
      </div>
      <DateRangePicker value={range} onChange={setRange} />

      {dashboard.isError ? (
        <ListError what="the dashboard" error={dashboard.error} onRetry={() => void dashboard.refetch()} />
      ) : !dashboard.data ? (
        <div role="status" aria-label="Loading the dashboard" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-2xl bg-card motion-reduce:animate-none" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard label="Net sales" value={formatKes(dashboard.data.kpis.netSales)} />
            <StatCard label="Profit" value={formatKes(dashboard.data.kpis.profit)} />
            <StatCard label="Sales made" value={dashboard.data.kpis.salesCount} />
            <StatCard label="Average sale" value={formatKes(dashboard.data.kpis.averageSale)} />
            <StatCard label="VAT" value={formatKes(dashboard.data.kpis.vat)} />
            <StatCard label="Returns" value={formatKes(dashboard.data.kpis.refunds)} />
            <StatCard
              label="Cash short/over"
              value={formatKes(dashboard.data.kpis.cashDifference)}
              tone={dashboard.data.kpis.cashDifference < 0 ? 'danger' : undefined}
            />
            <StatCard label="Stock value (cost)" value={formatKes(dashboard.data.kpis.stockValue)} />
          </div>
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-sm">
            <Link
              to="/stock?low=1"
              className={dashboard.data.kpis.lowStock > 0 ? 'font-medium text-danger' : 'text-muted'}
            >
              {dashboard.data.kpis.lowStock} products low on stock
            </Link>
            <Link to={report('shifts')} className="text-muted hover:text-foreground">
              {dashboard.data.kpis.openShifts} shifts open now
            </Link>
          </div>
          <SalesTrend days={dashboard.data.days} />
          <div className="grid gap-5 lg:grid-cols-2">
            <TopBars title="Top products" items={dashboard.data.topProducts} />
            <PaymentMix payments={dashboard.data.payments} />
          </div>
          <TopBars title="Sales per cashier" items={dashboard.data.cashiers} />
        </>
      )}
    </div>
  )
}
