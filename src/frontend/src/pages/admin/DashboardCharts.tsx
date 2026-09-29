// The dashboard's charts (Recharts). Colours come from the theme tokens, so the
// charts follow light and dark mode. Only the dashboard route loads this file.
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { Dashboard, Slice } from '../../services/reports'
import { formatDate, formatKes } from '../../utils/format'

const COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)']
const AXIS = { stroke: 'var(--muted)', fontSize: 12, tickLine: false, axisLine: false }
const TOOLTIP = {
  contentStyle: {
    background: 'var(--card)',
    border: '1px solid var(--border)',
    borderRadius: 12,
    color: 'var(--foreground)',
  },
  formatter: (value: unknown) => formatKes(Number(value)),
}

/** "KES 12k" on the axis, full amounts in the tooltip. */
const short = (v: number) => (Math.abs(v) >= 1000 ? `${Math.round(v / 1000)}k` : String(v))
const shortDay = (day: string) => formatDate(`${day}T12:00:00+03:00`).replace(/ \d{4}$/, '')

function ChartCard({ title, children, empty }: { title: string; children: React.ReactNode; empty?: boolean }) {
  return (
    <section className="rounded-2xl border border-border bg-card p-4 shadow-sm">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {empty ? <p className="py-16 text-center text-sm text-muted">No sales in these dates.</p> : children}
    </section>
  )
}

export function SalesTrend({ days }: { days: Dashboard['days'] }) {
  return (
    <ChartCard title="Sales and profit per day" empty={days.every((d) => d.sales === 0)}>
      <ResponsiveContainer width="100%" height={260}>
        <AreaChart data={days} margin={{ left: 0, right: 8, top: 8 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="day" tickFormatter={shortDay} minTickGap={24} {...AXIS} />
          <YAxis tickFormatter={short} width={40} {...AXIS} />
          <Tooltip {...TOOLTIP} labelFormatter={(d) => shortDay(String(d))} />
          <Legend />
          <Area
            type="monotone"
            dataKey="sales"
            name="Net sales"
            stroke={COLORS[0]}
            fill={COLORS[0]}
            fillOpacity={0.15}
            isAnimationActive={false}
          />
          <Area
            type="monotone"
            dataKey="profit"
            name="Profit"
            stroke={COLORS[1]}
            fill={COLORS[1]}
            fillOpacity={0.15}
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

export function TopBars({ title, items }: { title: string; items: Slice[] }) {
  return (
    <ChartCard title={title} empty={items.length === 0}>
      <ResponsiveContainer width="100%" height={Math.max(160, items.length * 44)}>
        <BarChart data={items} layout="vertical" margin={{ left: 0, right: 16 }}>
          <XAxis type="number" tickFormatter={short} {...AXIS} />
          <YAxis
            type="category"
            dataKey="label"
            width={110}
            tickFormatter={(v: string) => (v.length > 16 ? `${v.slice(0, 15)}…` : v)}
            {...AXIS}
          />
          <Tooltip {...TOOLTIP} cursor={{ fill: 'var(--background)' }} />
          <Bar dataKey="amount" name="Sales" fill={COLORS[0]} radius={[0, 6, 6, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}

export function PaymentMix({ payments }: { payments: Slice[] }) {
  const data = payments.filter((p) => p.amount > 0)
  return (
    <ChartCard title="How customers paid" empty={data.length === 0}>
      <ResponsiveContainer width="100%" height={240}>
        <PieChart>
          <Pie
            data={data}
            dataKey="amount"
            nameKey="label"
            innerRadius="55%"
            outerRadius="85%"
            paddingAngle={2}
            isAnimationActive={false}
          >
            {data.map((p, i) => (
              <Cell key={p.label} fill={COLORS[i % COLORS.length]} stroke="var(--card)" />
            ))}
          </Pie>
          <Tooltip {...TOOLTIP} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
