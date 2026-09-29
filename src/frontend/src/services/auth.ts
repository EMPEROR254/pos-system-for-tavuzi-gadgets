// Sign in and out: /api/auth.
import { refreshAccessToken, request, type SessionResponse } from './api'
import { useSession, type CurrentUser } from './session'

export async function login(username: string, password: string): Promise<CurrentUser> {
  const session = await request<SessionResponse>('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    skipRefresh: true,
  })
  useSession.getState().signIn(session.accessToken, session.user)
  return session.user
}

/**
 * On app start: use the refresh cookie (if any) to sign back in. If the server can't be reached,
 * the last person signed in on this till carries on offline (selling only; the server checks
 * everything when the sales sync).
 */
export async function restoreSession(): Promise<void> {
  const result = await refreshAccessToken()
  if (result === 'refused') useSession.getState().signOut()
  if (result === 'offline' && !useSession.getState().resumeOffline()) useSession.getState().signOut()
}

export async function logout(): Promise<void> {
  try {
    await request<void>('/api/auth/logout', { method: 'POST', skipRefresh: true })
  } finally {
    useSession.getState().signOut()
  }
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return request<void>('/api/auth/change-password', {
    method: 'POST',
    body: { currentPassword, newPassword },
  })
}
