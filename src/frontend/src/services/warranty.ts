// Warranty check by IMEI, serial or receipt number (/api/warranty).
import { request } from './api'

export type WarrantyResult = {
  receiptNo: string
  saleId: number
  soldAt: string
  productName: string
  serial: string | null
  warrantyEndsAt: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'NO_WARRANTY' | 'RETURNED'
  customerName: string | null
  customerPhone: string | null
}

export function lookupWarranty(q: string): Promise<WarrantyResult[]> {
  return request<WarrantyResult[]>(`/api/warranty?q=${encodeURIComponent(q)}`)
}
