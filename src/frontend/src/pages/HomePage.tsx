// The first screen after sign-in: a greeting and what this person may do.
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { stockSummary } from '../services/stock'
import { countIsDue } from '../utils/stock'
import { useSession } from '../services/session'
import { can, roleLabel, type Permission } from '../utils/permissions'

const ACCESS: { permission: Permission; label: string }[] = [
  { permission: 'MAKE_SALE', label: 'Make sales' },
  { permission: 'GIVE_DISCOUNT', label: 'Give discounts' },
  { permission: 'REFUND', label: 'Returns and exchanges' },
  { permission: 'VIEW_REPORTS', label: 'See reports' },
  { permission: 'MANAGE_PRODUCTS', label: 'Manage products and prices' },
  { permission: 'MANAGE_STOCK', label: 'Manage stock' },
  { permission: 'MANAGE_USERS', label: 'Manage staff accounts' },
  { permission: 'MANAGE_SETTINGS', label: 'Change shop settings' },
]

function greeting(): string {
  const hour = Number(
    new Intl.DateTimeFormat('en-KE', { hour: 'numeric', hour12: false, timeZone: 'Africa/Nairobi' }).format(new Date()),
  )
  return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
}

export default function HomePage() {
  const user = useSession((s) => s.user)
  if (!user) return null
  const firstName = user.fullName.split(' ')[0]

  return (
    <div className="animate-fade-in space-y-6">
      <section className="rounded-2xl border border-border bg-card p-6 shadow-sm sm:p-8">
        <p className="text-sm font-medium text-brand">{roleLabel(user.role)}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">
          {greeting()}, {firstName}
        </h1>
        <p className="mt-2 text-muted">Welcome to the Tavuzi Gadgets shop system.</p>
      </section>

      {can(user, 'MANAGE_STOCK') && <StockAlert />}

      <section className="rounded-2xl border border-border bg-card p-6 shadow-sm">
        <h2 className="font-semibold">Your access</h2>
        <ul className="mt-4 grid gap-2 sm:grid-cols-2">
          {ACCESS.map(({ permission, label }) => {
            const allowed = can(user, permission)
            return (
              <li key={permission} className="flex items-center gap-3 text-sm">
                <span
                  aria-hidden
                  className={`flex size-6 items-center justify-center rounded-full text-xs font-bold ${
                    allowed ? 'bg-brand-soft text-success' : 'bg-background text-muted'
                  }`}
                >
                  {allowed ? '✓' : '–'}
                </span>
                <span className={allowed ? '' : 'text-muted line-through'}>
                  {label}
                  <span className="sr-only">{allowed ? ' (allowed)' : ' (not allowed)'}</span>
                </span>
              </li>
            )
          })}
        </ul>
        {can(user, 'MAKE_SALE') && (
          <Link
            to="/sell"
            className="mt-6 mr-2 inline-flex min-h-11 items-center rounded-xl bg-brand px-4 text-sm font-medium text-brand-foreground shadow-sm hover:opacity-90"
          >
            Start selling
          </Link>
        )}
        {can(user, 'VIEW_REPORTS') && (
          <Link
            to="/dashboard"
            className="mt-6 mr-2 inline-flex min-h-11 items-center rounded-xl border border-border px-4 text-sm font-medium hover:bg-background"
          >
            Dashboard and reports
          </Link>
        )}
        {can(user, 'MANAGE_USERS') && (
          <Link
            to="/admin/users"
            className="mt-6 inline-flex min-h-11 items-center rounded-xl border border-border px-4 text-sm font-medium hover:bg-background"
          >
            Manage staff
          </Link>
        )}
      </section>
    </div>
  )
}

/** Low stock and an overdue weekly count, for the owner and manager. */
function StockAlert() {
  const summary = useQuery({ queryKey: ['stock', 'summary'], queryFn: stockSummary })
  if (!summary.data) return null
  const due = countIsDue(summary.data.lastCountAt)
  if (summary.data.lowStockCount === 0 && !due) return null
  return (
    <section role="status" className="space-y-2 rounded-2xl border border-danger/40 bg-card p-5 shadow-sm">
      {summary.data.lowStockCount > 0 && (
        <p>
          <span className="font-semibold text-danger">{summary.data.lowStockCount} products</span> are running low.{' '}
          <Link to="/stock?low=1" className="font-medium text-brand hover:underline">
            See what to reorder →
          </Link>
        </p>
      )}
      {due && (
        <p>
          The weekly stock count is due.{' '}
          <Link to="/stock/count" className="font-medium text-brand hover:underline">
            Start counting →
          </Link>
        </p>
      )}
    </section>
  )
}
