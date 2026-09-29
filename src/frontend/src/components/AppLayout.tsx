// The frame around every signed-in screen: header with the shop name,
// navigation (a drawer on phones), theme switch and the user's menu.
import { useRef, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { logout } from '../services/auth'
import { useSession } from '../services/session'
import { can, roleLabel, type Permission } from '../utils/permissions'
import { SyncAgent } from './SyncAgent'
import { SyncBadge } from './SyncBadge'
import { ThemeToggle } from './ThemeToggle'

/** more: shown under "More" on laptops (everything is listed in the phone menu). */
type NavItem = { to: string; label: string; permission?: Permission; more?: boolean }

// Later milestones add Reports here.
const NAV: NavItem[] = [
  { to: '/', label: 'Home' },
  { to: '/sell', label: 'Sell', permission: 'MAKE_SALE' },
  { to: '/sales', label: 'Sales', permission: 'MAKE_SALE' },
  { to: '/stock', label: 'Stock', permission: 'MANAGE_STOCK' },
  { to: '/customers', label: 'Customers', permission: 'MANAGE_CUSTOMERS' },
  { to: '/reports/mpesa', label: 'M-Pesa', permission: 'VIEW_REPORTS' },
  { to: '/warranty', label: 'Warranty', permission: 'MAKE_SALE', more: true },
  { to: '/messages', label: 'SMS', permission: 'SEND_PROMOTIONS', more: true },
  { to: '/admin/products', label: 'Products', permission: 'MANAGE_PRODUCTS', more: true },
  { to: '/settings/etims', label: 'KRA eTIMS', permission: 'VIEW_REPORTS', more: true },
  { to: '/admin/users', label: 'Staff', permission: 'MANAGE_USERS', more: true },
  { to: '/settings/hardware', label: 'Hardware', permission: 'MANAGE_SETTINGS', more: true },
  { to: '/sync', label: 'Sync', permission: 'MAKE_SALE', more: true },
  { to: '/account', label: 'My account', more: true },
]

export function AppLayout() {
  const user = useSession((s) => s.user)
  const [menuOpen, setMenuOpen] = useState(false)
  const more = useRef<HTMLDetailsElement>(null)
  const items = NAV.filter((item) => !item.permission || can(user, item.permission))

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex min-h-11 items-center rounded-xl px-2.5 text-sm font-medium transition-colors ${
      isActive ? 'bg-brand-soft text-foreground' : 'text-muted hover:bg-card hover:text-foreground'
    }`

  return (
    <div className="min-h-screen">
      <SyncAgent />
      <header className="sticky top-0 z-30 border-b print:hidden border-border bg-background/85 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2 sm:px-6">
          <button
            type="button"
            className="-ml-2 flex size-11 items-center justify-center rounded-xl text-xl xl:hidden"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? '✕' : '☰'}
          </button>
          <NavLink to="/" className="flex items-center gap-2 font-semibold tracking-tight">
            <span
              aria-hidden
              className="flex size-8 items-center justify-center rounded-lg bg-brand text-sm font-bold text-brand-foreground"
            >
              TG
            </span>
            <span className="hidden sm:inline">Tavuzi Gadgets</span>
          </NavLink>
          <nav aria-label="Main" className="ml-4 hidden items-center gap-1 xl:flex">
            {items
              .filter((item) => !item.more)
              .map((item) => (
                <NavLink key={item.to} to={item.to} end className={linkClass}>
                  {item.label}
                </NavLink>
              ))}
            <details ref={more} className="relative">
              <summary className="flex min-h-11 cursor-pointer list-none items-center rounded-xl px-2.5 text-sm font-medium text-muted hover:bg-card hover:text-foreground">
                More ▾
              </summary>
              <div className="absolute left-0 top-full z-40 mt-1 w-52 rounded-xl border border-border bg-card p-1 shadow-lg">
                {items
                  .filter((item) => item.more)
                  .map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      end
                      className={linkClass}
                      onClick={() => more.current?.removeAttribute('open')}
                    >
                      {item.label}
                    </NavLink>
                  ))}
              </div>
            </details>
          </nav>
          <div className="ml-auto flex items-center gap-3">
            <SyncBadge />
            <ThemeToggle />
            <div className="hidden text-right leading-tight lg:block">
              <p className="text-sm font-medium">{user?.fullName}</p>
              <p className="text-xs text-muted">{user && roleLabel(user.role)}</p>
            </div>
            <button
              type="button"
              onClick={() => void logout()}
              className="hidden min-h-11 rounded-xl px-3 text-sm text-muted hover:bg-card hover:text-foreground xl:block"
            >
              Sign out
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav aria-label="Main" className="animate-fade-in border-t border-border px-4 py-3 xl:hidden">
            <p className="px-3 pb-2 text-sm">
              <span className="font-medium">{user?.fullName}</span>
              <span className="text-muted"> · {user && roleLabel(user.role)}</span>
            </p>
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end className={linkClass} onClick={() => setMenuOpen(false)}>
                {item.label}
              </NavLink>
            ))}
            <button
              type="button"
              onClick={() => void logout()}
              className="flex min-h-11 w-full items-center rounded-xl px-3 text-sm font-medium text-danger hover:bg-card"
            >
              Sign out
            </button>
          </nav>
        )}
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 print:p-0">
        <Outlet />
      </main>
    </div>
  )
}
