// Header badge: how many sales are waiting on this till to reach the server,
// and how many need a manager's attention. Links to the Sync screen.
import { Link } from 'react-router'
import { useOffline } from '../services/offline'

export function SyncBadge() {
  const waiting = useOffline((s) => s.waiting)
  if (waiting.length === 0) return null
  const problems = waiting.filter((w) => w.problem).length
  return (
    <Link
      to="/sync"
      className={`inline-flex min-h-11 items-center rounded-xl px-3 text-xs font-semibold ${
        problems > 0 ? 'bg-danger text-white' : 'bg-brand-soft text-foreground'
      }`}
    >
      {problems > 0
        ? `${problems} sale${problems === 1 ? '' : 's'} need attention`
        : `${waiting.length} waiting to sync`}
    </Link>
  )
}
