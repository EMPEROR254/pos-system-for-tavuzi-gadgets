// Suppliers, deliveries (goods received) and what the shop owes (/api/suppliers, /api/purchases).
import { request } from './api'
import { toQuery } from './query'
import type { Page } from './users'

export type SupplierPaymentMethod = 'CASH' | 'MPESA' | 'BANK' | 'CHEQUE'

export const SUPPLIER_PAYMENT_LABELS: Record<SupplierPaymentMethod, string> = {
  CASH: 'Cash',
  MPESA: 'M-Pesa',
  BANK: 'Bank transfer',
  CHEQUE: 'Cheque',
}

export type Supplier = {
  id: number
  name: string
  phone: string | null
  email: string | null
  notes: string | null
  active: boolean
  purchased: number
  paid: number
  balance: number
}

export type SupplierInput = { name: string; phone?: string; email?: string; notes?: string; active: boolean }

export type StatementLine = { date: string; description: string; amount: number; balance: number }

export type DeliverySummary = {
  id: number
  supplierName: string
  invoiceNo: string | null
  total: number
  receivedAt: string
}

export type DeliveryInput = {
  supplierId: number
  invoiceNo?: string
  note?: string
  lines: { productId: number; unitId: number; quantity: number; unitCost: number; serials?: string[] }[]
  amountPaid?: number
  paymentMethod?: SupplierPaymentMethod
  paymentReference?: string
}

export const listSuppliers = (f: { q?: string; active?: boolean; page?: number; size?: number }) =>
  request<Page<Supplier>>(`/api/suppliers${toQuery(f)}`)
export const createSupplier = (body: SupplierInput) => request<Supplier>('/api/suppliers', { method: 'POST', body })
export const updateSupplier = (id: number, body: SupplierInput) =>
  request<Supplier>(`/api/suppliers/${id}`, { method: 'PUT', body })
export const supplierStatement = (id: number) => request<StatementLine[]>(`/api/suppliers/${id}/statement`)
export const paySupplier = (id: number, body: { amount: number; method: SupplierPaymentMethod; reference?: string }) =>
  request<Supplier>(`/api/suppliers/${id}/payments`, { method: 'POST', body })

export const listDeliveries = (f: { supplierId?: number; page?: number; size?: number }) =>
  request<Page<DeliverySummary>>(`/api/purchases${toQuery(f)}`)
export const receiveDelivery = (body: DeliveryInput) =>
  request<{ id: number; total: number }>('/api/purchases', { method: 'POST', body })
