// Sales, receipts and returns (/api/sales).
import { request } from './api'
import type { Page } from './users'

export type PaymentMethod = 'CASH' | 'MPESA_TILL' | 'MPESA_PAYBILL' | 'CARD'
export type PriceTier = 'RETAIL' | 'WHOLESALE'
export type SaleStatus = 'COMPLETED' | 'PARTLY_RETURNED' | 'RETURNED'
export type Approval = { username: string; password: string }

export const PAYMENT_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  MPESA_TILL: 'M-Pesa Till',
  MPESA_PAYBILL: 'M-Pesa Paybill',
  CARD: 'Card',
}

export type CheckoutLine = {
  productId: number
  unitId: number
  quantity: number
  discount?: number
  serials?: string[]
}

export type CheckoutPayment = { method: PaymentMethod; amount: number; reference?: string }

export type CheckoutRequest = {
  clientRef: string
  priceTier: PriceTier
  lines: CheckoutLine[]
  payments: CheckoutPayment[]
  customerName?: string
  customerPhone?: string
  approval?: Approval
}

export type SaleLineView = {
  id: number
  productName: string
  unitName: string
  quantity: number
  returnedQuantity: number
  unitPrice: number
  discount: number
  lineTotal: number
  vatCode: string
  vatRate: number
  vatAmount: number
  warrantyEndsAt: string | null
  serials: { serial: string; returned: boolean }[]
}

export type Sale = {
  id: number
  receiptNo: string
  createdAt: string
  cashierName: string
  status: SaleStatus
  priceTier: PriceTier
  customerName: string | null
  customerPhone: string | null
  subtotal: number
  discountTotal: number
  vatTotal: number
  total: number
  paidTotal: number
  changeDue: number
  lines: SaleLineView[]
  payments: { method: PaymentMethod; label: string; amount: number; reference: string | null }[]
}

export type SaleSummary = {
  id: number
  receiptNo: string
  createdAt: string
  cashierName: string
  status: SaleStatus
  total: number
  customerName: string | null
}

export type Receipt = {
  shopName: string
  shopDetails: string[]
  paymentInstructions: string[]
  sale: Sale
  vatBreakdown: { code: string; ratePercent: number; taxable: number; vat: number; gross: number }[]
  returnPolicy: string | null
  footer: string[]
}

export type SaleFilters = {
  from?: string
  to?: string
  receipt?: string
  status?: SaleStatus | ''
  page?: number
  size?: number
}

export type ReturnRequest = {
  lines: { saleLineId: number; quantity: number; serials?: string[] }[]
  reason: string
  refundMethod: PaymentMethod
  approval?: Approval
}

export function checkout(sale: CheckoutRequest): Promise<Sale> {
  return request<Sale>('/api/sales', { method: 'POST', body: sale })
}

export function listSales(filters: SaleFilters): Promise<Page<SaleSummary>> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return request<Page<SaleSummary>>(`/api/sales?${params}`)
}

export function getReceipt(saleId: number): Promise<Receipt> {
  return request<Receipt>(`/api/sales/${saleId}/receipt`)
}

export function printReceipt(saleId: number): Promise<void> {
  return request<void>(`/api/sales/${saleId}/receipt/print`, { method: 'POST' })
}

export function returnItems(saleId: number, body: ReturnRequest) {
  return request<{ id: number; receiptNo: string; refundAmount: number; vatAmount: number; status: SaleStatus }>(
    `/api/sales/${saleId}/returns`,
    { method: 'POST', body },
  )
}
