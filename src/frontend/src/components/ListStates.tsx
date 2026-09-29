// The states every list shows: loading skeleton, error with retry, empty, and
// page buttons. Shared so all screens look and behave the same.
import type { ReactNode } from 'react'
import { Button } from './Button'

export function ListSkeleton({ label, rows = 4 }: { label: string; rows?: number }) {
  return (
    <div role="status" aria-label={label} className="space-y-2">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-14 animate-pulse rounded-xl bg-card motion-reduce:animate-none" />
      ))}
    </div>
  )
}

export function ListError({ what, error, onRetry }: { what: string; error: Error; onRetry: () => void }) {
  return (
    <div role="alert" className="rounded-2xl border border-danger/40 bg-card p-6 text-center">
      <p className="font-medium text-danger">Could not load {what}.</p>
      <p className="mt-1 text-sm text-muted">{error.message}</p>
      <Button variant="secondary" className="mt-4" onClick={onRetry}>
        Try again
      </Button>
    </div>
  )
}

export function ListEmpty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center">
      <p className="font-medium">{title}</p>
      {children && <p className="mt-1 text-sm text-muted">{children}</p>}
    </div>
  )
}

type PagerProps = { page: number; totalPages: number; total: number; noun: string; onPage: (page: number) => void }

export function Pager({ page, totalPages, total, noun, onPage }: PagerProps) {
  return (
    <nav aria-label="Pages" className="flex items-center justify-between gap-3 text-sm">
      <p className="text-muted">
        {total} {noun} · page {page + 1} of {Math.max(totalPages, 1)}
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
          Next
        </Button>
      </div>
    </nav>
  )
}

/** A number card for the top of a screen (e.g. "Low on stock: 4"). */
export function StatCard({ label, value, tone }: { label: string; value: ReactNode; tone?: 'danger' }) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
      <p className="text-sm text-muted">{label}</p>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${tone === 'danger' ? 'text-danger' : ''}`}>{value}</p>
    </div>
  )
}
