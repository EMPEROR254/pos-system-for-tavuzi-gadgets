// Stock: levels, low-stock alerts, history, adjustments and counts (/api/stock).
import { request } from './api'
import { toQuery } from './query'
import type { Page } from './users'

export type StockRow = {
  productId: number
  name: string
  sku: string | null
  category: string | null
  serialType: 'NONE' | 'IMEI' | 'SERIAL'
  baseUnit: string
  stockOnHand: number
  reorderLevel: number
  low: boolean
  costPrice: number
  stockValue: number
}

export type StockSummary = { lowStockCount: number; stockValue: number; lastCountAt: string | null }

export type Movement = {
  id: number
  type: 'SALE' | 'RETURN' | 'PURCHASE' | 'ADJUSTMENT' | 'COUNT'
  quantity: number
  balanceAfter: number
  reason: string | null
  note: string | null
  reference: string | null
  userName: string
  createdAt: string
}

export type AdjustmentReason =
  'OPENING_STOCK' | 'DAMAGED' | 'LOST_OR_STOLEN' | 'WARRANTY_REPAIR' | 'RETURNED_TO_SUPPLIER' | 'CORRECTION'

export const REASON_LABELS: Record<AdjustmentReason, string> = {
  OPENING_STOCK: 'Opening stock / found',
  DAMAGED: 'Damaged',
  LOST_OR_STOLEN: 'Lost or stolen',
  WARRANTY_REPAIR: 'Used for a warranty repair',
  RETURNED_TO_SUPPLIER: 'Returned to supplier',
  CORRECTION: 'Correction',
}

export type CountLine = {
  productId: number
  name: string
  category: string | null
  serialType: string
  expected: number
  counted: number | null
  difference: number | null
}

export type StockCount = {
  id: number
  status: 'OPEN' | 'FINISHED' | 'CANCELLED'
  category: string | null
  startedAt: string
  finishedAt: string | null
  products: number
  countedProducts: number
  differenceValue: number
  lines: CountLine[] | null
}

export const listStock = (f: { q?: string; category?: string; low?: boolean; page?: number; size?: number }) =>
  request<Page<StockRow>>(`/api/stock${toQuery(f)}`)
export const stockSummary = () => request<StockSummary>('/api/stock/summary')
export const stockHistory = (productId: number, page = 0) =>
  request<Page<Movement>>(`/api/stock/${productId}/history?page=${page}`)
export const serialsInStock = (productId: number) => request<string[]>(`/api/stock/${productId}/serials`)
export const adjustStock = (body: {
  productId: number
  quantity: number
  reason: AdjustmentReason
  note?: string
  serials?: string[]
}) => request<{ stockOnHand: number }>('/api/stock/adjustments', { method: 'POST', body })

export const recentCounts = () => request<StockCount[]>('/api/stock/counts')
export const getCount = (id: number) => request<StockCount>(`/api/stock/counts/${id}`)
export const startCount = (category?: string) =>
  request<StockCount>('/api/stock/counts', { method: 'POST', body: { category: category || null } })
export const saveCount = (id: number, entries: { productId: number; counted: number | null }[]) =>
  request<StockCount>(`/api/stock/counts/${id}`, { method: 'PUT', body: entries })
export const finishCount = (id: number) => request<StockCount>(`/api/stock/counts/${id}/finish`, { method: 'POST' })
export const cancelCount = (id: number) => request<StockCount>(`/api/stock/counts/${id}/cancel`, { method: 'POST' })
