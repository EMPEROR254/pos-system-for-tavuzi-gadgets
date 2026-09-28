// Root component: providers, the routes and app-wide pieces (offline banner,
// toasts). Screens live in src/pages and are loaded only when opened, so the
// first screen stays small and fast.
import { QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useEffect } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { AppLayout } from './components/AppLayout'
import { OfflineBanner } from './components/OfflineBanner'
import { RequireAuth, RequirePermission, SplashScreen } from './components/RequireAuth'
import { Toaster } from './components/Toaster'
import { restoreSession } from './services/auth'
import { queryClient } from './services/queryClient'
import { useSession } from './services/session'

const LoginPage = lazy(() => import('./pages/LoginPage'))
const HomePage = lazy(() => import('./pages/HomePage'))
const AccountPage = lazy(() => import('./pages/AccountPage'))
const UsersPage = lazy(() => import('./pages/admin/UsersPage'))
const ProductsPage = lazy(() => import('./pages/admin/ProductsPage'))
const SellPage = lazy(() => import('./pages/sell/SellPage'))
const SalesPage = lazy(() => import('./pages/sales/SalesPage'))
const ReceiptPage = lazy(() => import('./pages/sales/ReceiptPage'))
const WarrantyPage = lazy(() => import('./pages/WarrantyPage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

export function App() {
  useEffect(() => {
    if (useSession.getState().status === 'checking') void restoreSession()
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <OfflineBanner />
        <Suspense fallback={<SplashScreen />}>
          <AppRoutes />
        </Suspense>
        <Toaster />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<HomePage />} />
        <Route path="account" element={<AccountPage />} />
        <Route
          path="sell"
          element={
            <RequirePermission permission="MAKE_SALE">
              <SellPage />
            </RequirePermission>
          }
        />
        <Route
          path="sales"
          element={
            <RequirePermission permission="MAKE_SALE">
              <SalesPage />
            </RequirePermission>
          }
        />
        <Route
          path="sales/:id/receipt"
          element={
            <RequirePermission permission="MAKE_SALE">
              <ReceiptPage />
            </RequirePermission>
          }
        />
        <Route
          path="warranty"
          element={
            <RequirePermission permission="MAKE_SALE">
              <WarrantyPage />
            </RequirePermission>
          }
        />
        <Route
          path="admin/products"
          element={
            <RequirePermission permission="MANAGE_PRODUCTS">
              <ProductsPage />
            </RequirePermission>
          }
        />
        <Route
          path="admin/users"
          element={
            <RequirePermission permission="MANAGE_USERS">
              <UsersPage />
            </RequirePermission>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
