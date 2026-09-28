// Shown for a page address that doesn't exist.
import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="mx-auto max-w-md animate-fade-in rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
      <h1 className="text-lg font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-muted">This page doesn't exist or was moved.</p>
      <Link to="/" className="mt-4 inline-flex min-h-11 items-center font-medium text-brand hover:underline">
        Back to home
      </Link>
    </section>
  )
}
