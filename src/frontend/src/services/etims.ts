// KRA eTIMS invoices: what reached KRA, what is waiting, what KRA refused (/api/etims).
import { request } from './api'
import { toQuery } from './query'
import type { Page } from './users'

export type EtimsInvoice = {
  id: number
  invoiceNo: number
  kind: 'SALE' | 'REFUND'
  saleId: number
  receiptNo: string | null
  status: 'PENDING' | 'SENT' | 'REJECTED'
  attempts: number
  lastError: string | null
  createdAt: string
  sentAt: string | null
}

export type EtimsSummary = { mode: 'OFF' | 'SANDBOX' | 'PRODUCTION'; pending: number; sent: number; rejected: number }

export const etimsSummary = () => request<EtimsSummary>('/api/etims/summary')
export const listEtimsInvoices = (f: { status?: string; page?: number }) =>
  request<Page<EtimsInvoice>>(`/api/etims/invoices${toQuery(f)}`)
export const retryEtimsInvoice = (id: number) =>
  request<EtimsInvoice>(`/api/etims/invoices/${id}/retry`, { method: 'POST' })
