// Say clearly when the connection drops - and that selling carries on.
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
    <div role="alert" className="bg-danger px-4 py-2 print:hidden text-center text-sm font-medium text-white">
      No connection. You can keep selling - sales are saved on this till and sent when the connection is back.
    </div>
  )
}
