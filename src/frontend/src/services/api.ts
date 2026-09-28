// Calls to the backend API. One small function per endpoint lives in a file
// per area (e.g. services/users.ts); they all go through request() here.
//
// request() adds the access token, and when the server says 401 (token ran
// out after 15 minutes) it quietly gets a new one with the refresh cookie and
// tries once more. If that fails too, the user is signed out.
import { useSession, type CurrentUser } from './session'

/** An API error with a message that is safe to show, plus per-field messages for forms. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown; skipRefresh?: boolean }

// Calls stay relative (/api/...): Spring Boot serves this app, and in
// development Vite forwards /api to Spring Boot on :8080.
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 401 && !options.skipRefresh && useSession.getState().accessToken) {
    const refreshed = await refreshAccessToken()
    if (refreshed) return parse<T>(await send(path, options))
    useSession.getState().signOut()
  }
  return parse<T>(response)
}

async function send(path: string, { body, skipRefresh: _skip, headers, ...init }: RequestOptions) {
  const token = useSession.getState().accessToken
  try {
    return await fetch(path, {
      ...init,
      credentials: 'same-origin',
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check the network and try again.')
  }
}

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T
  }
  let problem: { detail?: string; errors?: Record<string, string> } = {}
  try {
    problem = await response.json()
  } catch {
    // Not JSON (e.g. a proxy error page): fall back to a general message.
  }
  throw new ApiError(
    response.status,
    problem.detail ?? `Something went wrong (${response.status}). Please try again.`,
    problem.errors ?? {},
  )
}

// Several requests can hit 401 at the same moment; they share one refresh.
let refreshing: Promise<boolean> | null = null

export function refreshAccessToken(): Promise<boolean> {
  refreshing ??= (async () => {
    try {
      const response = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
      if (!response.ok) return false
      const session = (await response.json()) as SessionResponse
      useSession.getState().signIn(session.accessToken, session.user)
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

/** Body of login and refresh. */
export type SessionResponse = {
  accessToken: string
  expiresInSeconds: number
  user: CurrentUser
}
