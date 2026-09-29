// Runs in the background while someone is signed in: keeps the offline copy of
// the catalogue fresh, and sends sales saved offline as soon as the server can
// be reached again (on the browser's "online" event and every 30 seconds).
import { useEffect } from 'react'
import { refreshCatalogue, syncWaitingSales, useOffline } from '../services/offline'
import { useSession } from '../services/session'
import { can } from '../utils/permissions'
import { toast } from '../utils/toast'

const SYNC_EVERY_MS = 30_000
const CATALOGUE_EVERY_MS = 10 * 60_000

export function SyncAgent() {
  const user = useSession((s) => s.user)
  const sells = can(user, 'MAKE_SALE')

  useEffect(() => {
    if (!sells) return
    let lastCatalogue = 0
    const tick = async () => {
      if (useOffline.getState().waiting.some((w) => !w.problem)) {
        const synced = await syncWaitingSales()
        if (synced.length > 0)
          toast.success(`${synced.length} offline sale${synced.length === 1 ? '' : 's'} sent to the server.`)
      }
      if (Date.now() - lastCatalogue > CATALOGUE_EVERY_MS) {
        try {
          await refreshCatalogue()
          lastCatalogue = Date.now()
        } catch {
          // offline: keep the copy we have
        }
      }
    }
    void tick()
    const timer = window.setInterval(() => void tick(), SYNC_EVERY_MS)
    const onOnline = () => void tick()
    window.addEventListener('online', onOnline)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('online', onOnline)
    }
  }, [sells])

  return null
}
