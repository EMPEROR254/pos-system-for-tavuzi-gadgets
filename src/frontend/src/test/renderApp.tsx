// Test helper: render the app's routes at a given address, optionally signed in.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { Suspense } from 'react'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App'
import { Toaster } from '../components/Toaster'
import { useSession, type CurrentUser } from '../services/session'
import type { Role } from '../utils/permissions'

const PERMISSIONS: Record<Role, CurrentUser['permissions']> = {
  OWNER: [
    'MAKE_SALE',
    'GIVE_DISCOUNT',
    'REFUND',
    'VIEW_REPORTS',
    'MANAGE_PRODUCTS',
    'MANAGE_STOCK',
    'MANAGE_USERS',
    'MANAGE_SETTINGS',
    'MANAGE_CUSTOMERS',
    'SEND_PROMOTIONS',
  ],
  MANAGER: [
    'MAKE_SALE',
    'GIVE_DISCOUNT',
    'VIEW_REPORTS',
    'MANAGE_PRODUCTS',
    'MANAGE_STOCK',
    'MANAGE_CUSTOMERS',
    'SEND_PROMOTIONS',
  ],
  CASHIER: ['MAKE_SALE'],
}

export function userWithRole(role: Role, id = 1): CurrentUser {
  return {
    id,
    username: role.toLowerCase(),
    fullName: `Test ${role.toLowerCase()}`,
    role,
    permissions: PERMISSIONS[role],
  }
}

export function renderApp(path: string, role?: Role) {
  if (role) useSession.setState({ status: 'signedIn', accessToken: 'token', user: userWithRole(role) })
  else useSession.setState({ status: 'signedOut', accessToken: null, user: null })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Suspense fallback={<p>Loading…</p>}>
          <AppRoutes />
        </Suspense>
        <Toaster />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
