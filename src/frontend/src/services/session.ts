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
}

export const useSession = create<SessionState>((set) => ({
  status: 'checking',
  accessToken: null,
  user: null,
  signIn: (accessToken, user) => set({ status: 'signedIn', accessToken, user }),
  signOut: () => {
    // Forget cached lists so the next person on this till never sees them.
    queryClient.clear()
    set({ status: 'signedOut', accessToken: null, user: null })
  },
}))
