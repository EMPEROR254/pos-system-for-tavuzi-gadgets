// Tabs across the stock screens: levels, suppliers & deliveries, and counts.
import { NavLink } from 'react-router'

const TABS = [
  { to: '/stock', label: 'Stock levels' },
  { to: '/stock/suppliers', label: 'Suppliers & deliveries' },
  { to: '/stock/count', label: 'Stock count' },
]

export function StockNav() {
  return (
    <nav aria-label="Stock" className="-mx-4 flex gap-1 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      {TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end
          className={({ isActive }) =>
            `flex min-h-11 shrink-0 items-center rounded-xl px-4 text-sm font-medium ${
              isActive ? 'bg-brand text-brand-foreground' : 'bg-card text-muted hover:text-foreground'
            }`
          }
        >
          {t.label}
        </NavLink>
      ))}
    </nav>
  )
}
