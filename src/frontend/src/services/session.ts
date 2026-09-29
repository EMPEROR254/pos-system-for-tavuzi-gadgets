// Who is signed in. The access token lives only in memory (never in
// localStorage), so a script injected into the page can't steal it from
// storage; after a page reload the refresh cookie gets a new one.
import { create } from 'zustand'
import type { Permission, Role } from '../utils/permissions'
import { queryClient } from './queryClient'

export type CurrentUser = {
  id: number
  username: string
  fullName: string
  role: Role
  permissions: Permission[]
}

type SessionState = {
  /** 'checking' while the app asks the server whether a sign-in is still valid. */
  status: 'checking' | 'signedIn' | 'signedOut'
  accessToken: string | null
  user: CurrentUser | null
  signIn: (accessToken: string, user: CurrentUser) => void
  signOut: () => void
  /** Continue as the last user of this till while the server can't be reached. */
  resumeOffline: () => boolean
}

// Who last signed in on this till (name, role, permissions - no password or token), so the till
// can keep selling after a reload while the server is unreachable.
const LAST_USER = 'tavuzi-last-user'

function rememberUser(user: CurrentUser | null) {
  try {
    if (user) localStorage.setItem(LAST_USER, JSON.stringify(user))
    else localStorage.removeItem(LAST_USER)
  } catch {
    // private mode: offline restart just won't be possible
  }
}

function lastUser(): CurrentUser | null {
  try {
    const text = localStorage.getItem(LAST_USER)
    return text ? (JSON.parse(text) as CurrentUser) : null
  } catch {
    return null
  }
}

export const useSession = create<SessionState>((set) => ({
  status: 'checking',
  accessToken: null,
  user: null,
  signIn: (accessToken, user) => {
    rememberUser(user)
    set({ status: 'signedIn', accessToken, user })
  },
  signOut: () => {
    // Forget cached lists so the next person on this till never sees them.
    queryClient.clear()
    rememberUser(null)
    set({ status: 'signedOut', accessToken: null, user: null })
  },
  resumeOffline: () => {
    const user = lastUser()
    if (user) set({ status: 'signedIn', accessToken: null, user })
    return user !== null
  },
}))
