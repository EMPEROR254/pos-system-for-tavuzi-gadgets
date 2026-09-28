// The first screen. Each screen of the system is a file in src/pages.
import { formatKes } from '../utils/format'

export function HomePage() {
  return (
    <section className="animate-slide-up rounded-2xl border border-border bg-card p-8 shadow-sm">
      <p className="text-sm font-medium text-brand">Welcome</p>
      <h2 className="mt-1 text-xl font-semibold">Your system is ready to build.</h2>
      <p className="mt-2 text-muted">
        Example amount: <span className="tabular-nums">{formatKes(1250)}</span>
      </p>
    </section>
  )
}
