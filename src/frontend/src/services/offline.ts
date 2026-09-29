// Selling without the server. The till keeps a copy of the catalogue (prices,
// stock, IMEIs on the shelf) and, when the server can't be reached, saves each
// sale here instead. Waiting sales are sent as soon as the server is back; each
// keeps its clientRef, so a sale is never saved twice even if sending is retried.
//
// If the server refuses a waiting sale (e.g. that IMEI was sold on another
// till meanwhile), it is kept as "needs attention" for a manager - never lost.
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { ApiError, request } from './api'
import type { SellableProduct } from './products'
import { checkout, type CheckoutRequest, type Sale } from './sales'

export type Catalogue = {
  generatedAt: string
  products: SellableProduct[]
  serialsInStock: Record<string, string[]>
}

/** What the till shows and prints for a sale it could not send yet. */
export type OfflineReceipt = {
  lines: { name: string; unitName: string; quantity: number; total: number }[]
  payments: { label: string; amount: number; reference?: string }[]
  change: number
}

export type WaitingSale = {
  clientRef: string
  request: CheckoutRequest
  total: number
  itemCount: number
  cashierName: string
  createdAt: string
  receipt: OfflineReceipt
  /** Set when the server refused it: why, for the person fixing it. */
  problem?: string
}

type OfflineState = {
  catalogue: Catalogue | null
  waiting: WaitingSale[]
  syncing: boolean
  setCatalogue: (c: Catalogue) => void
  add: (sale: WaitingSale) => void
  remove: (clientRef: string) => void
  setProblem: (clientRef: string, problem: string | undefined) => void
  setSyncing: (syncing: boolean) => void
}

export const useOffline = create<OfflineState>()(
  persist(
    (set) => ({
      catalogue: null,
      waiting: [],
      syncing: false,
      setCatalogue: (catalogue) => set({ catalogue }),
      add: (sale) => set((s) => ({ waiting: [...s.waiting.filter((w) => w.clientRef !== sale.clientRef), sale] })),
      remove: (clientRef) => set((s) => ({ waiting: s.waiting.filter((w) => w.clientRef !== clientRef) })),
      setProblem: (clientRef, problem) =>
        set((s) => ({ waiting: s.waiting.map((w) => (w.clientRef === clientRef ? { ...w, problem } : w)) })),
      setSyncing: (syncing) => set({ syncing }),
    }),
    { name: 'tavuzi-offline', version: 1, partialize: (s) => ({ catalogue: s.catalogue, waiting: s.waiting }) },
  ),
)

/** True when the error means "the server can't be reached" (not "the server said no"). */
export function isUnreachable(e: unknown): boolean {
  return e instanceof ApiError && (e.status === 0 || e.status >= 502)
}

export async function refreshCatalogue(): Promise<void> {
  const catalogue = await request<Catalogue>('/api/sync/catalogue')
  useOffline.getState().setCatalogue(catalogue)
}

/** Search the saved catalogue: an exact barcode/SKU first, else names containing the words. */
export function searchOffline(query: string): SellableProduct[] {
  const products = useOffline.getState().catalogue?.products ?? []
  const q = query.trim().toLowerCase()
  if (!q) return []
  const exact = products.filter(
    (p) => p.sku?.toLowerCase() === q || p.units.some((u) => u.barcode?.toLowerCase() === q),
  )
  if (exact.length > 0) return exact
  const words = q.split(/\s+/)
  return products.filter((p) => words.every((w) => p.name.toLowerCase().includes(w))).slice(0, 20)
}

export function serialsOffline(productId: number): string[] {
  return useOffline.getState().catalogue?.serialsInStock[String(productId)] ?? []
}

/**
 * Sends every waiting sale. Stops at the first "can't reach the server" (try again later);
 * refused sales are marked for attention and the rest carry on. Returns the sales that synced.
 */
export async function syncWaitingSales(): Promise<Sale[]> {
  const store = useOffline.getState()
  if (store.syncing) return []
  store.setSyncing(true)
  const synced: Sale[] = []
  try {
    for (const sale of useOffline.getState().waiting.filter((w) => !w.problem)) {
      try {
        synced.push(await checkout(sale.request))
        useOffline.getState().remove(sale.clientRef)
      } catch (e) {
        if (isUnreachable(e)) break
        useOffline.getState().setProblem(sale.clientRef, e instanceof Error ? e.message : 'Refused by the server.')
      }
    }
  } finally {
    useOffline.getState().setSyncing(false)
  }
  return synced
}

/**
 * A manager fixed the reason (e.g. received the IMEI into stock): try this sale again.
 * Returns true when it reached the server.
 */
export async function retryWaitingSale(clientRef: string): Promise<boolean> {
  useOffline.getState().setProblem(clientRef, undefined)
  await syncWaitingSales()
  return !useOffline.getState().waiting.some((w) => w.clientRef === clientRef)
}
