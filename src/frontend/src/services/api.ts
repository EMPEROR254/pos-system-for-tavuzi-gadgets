// Calls to the backend API. One small function per endpoint lives in a file
// per area (e.g. services/sales.ts); they all go through request() here.

/** Base URL of the API ('' = same site, e.g. when Spring Boot serves this app). */
const API_URL = import.meta.env.VITE_API_URL ?? ''

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) throw new Error(`Request failed (${response.status}): ${path}`)
  return (await response.json()) as T
}
