// The system works online only: say so clearly when the connection drops.
import { useEffect, useState } from 'react'

export function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine)

  useEffect(() => {
    const up = () => setOnline(true)
    const down = () => setOnline(false)
    window.addEventListener('online', up)
    window.addEventListener('offline', down)
    return () => {
      window.removeEventListener('online', up)
      window.removeEventListener('offline', down)
    }
  }, [])

  if (online) return null
  return (
    <div role="alert" className="bg-danger px-4 py-2 text-center text-sm font-medium text-white">
      No connection to the network. Changes can't be saved until it is back.
    </div>
  )
}
