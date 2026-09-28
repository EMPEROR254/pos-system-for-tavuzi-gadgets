// Waits until the user stops typing before using a value (e.g. a search box),
// so we don't call the server on every key press.
import { useEffect, useState } from 'react'

export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
