// Route guards: send signed-out users to the login page, and show a clear
// message when a role may not open a screen (the server refuses it anyway).
import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { useSession } from '../services/session'
import { can, type Permission } from '../utils/permissions'

export function RequireAuth({ children }: { children: ReactNode }) {
  const status = useSession((s) => s.status)
  const location = useLocation()
  if (status === 'checking') return <SplashScreen />
  if (status === 'signedOut') {
    const next = location.pathname === '/' ? '' : `?next=${encodeURIComponent(location.pathname + location.search)}`
    return <Navigate to={`/login${next}`} replace />
  }
  return children
}

export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const user = useSession((s) => s.user)
  if (can(user, permission)) return children
  return (
    <section className="mx-auto max-w-md animate-fade-in rounded-2xl border border-border bg-card p-8 text-center shadow-sm">
      <h1 className="text-lg font-semibold">Not available for your role</h1>
      <p className="mt-2 text-sm text-muted">Ask the owner if you need access to this screen.</p>
      <Link to="/" className="mt-4 inline-flex min-h-11 items-center font-medium text-brand hover:underline">
        Back to home
      </Link>
    </section>
  )
}

export function SplashScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Loading">
      <span className="size-8 animate-spin rounded-full border-4 border-brand border-t-transparent motion-reduce:animate-none" />
    </div>
  )
}
