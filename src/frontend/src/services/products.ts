// Products: the till's quick lookup and catalogue management (/api/products).
import { request } from './api'
import type { Page } from './users'

export type VatCategory = 'STANDARD' | 'ZERO_RATED' | 'EXEMPT'
export type SerialType = 'NONE' | 'IMEI' | 'SERIAL'

export type Unit = {
  id: number
  name: string
  factor: number
  barcode: string | null
  retailPrice: number
  wholesalePrice: number | null
}

/** What the till sees (no cost price). vatRate is a percent, e.g. 16. */
export type SellableProduct = {
  id: number
  name: string
  sku: string | null
  vatCategory: VatCategory
  vatRate: number
  serialType: SerialType
  warrantyMonths: number
  units: Unit[]
}

export type Product = {
  id: number
  name: string
  sku: string | null
  category: string | null
  vatCategory: VatCategory
  serialType: SerialType
  warrantyMonths: number
  costPrice: number
  active: boolean
  units: Unit[]
}

export type UnitInput = {
  id?: number | null
  name: string
  factor: number
  barcode?: string | null
  retailPrice: number
  wholesalePrice?: number | null
}

export type ProductInput = Omit<Product, 'id' | 'units'> & { units: UnitInput[] }

export type ProductFilters = {
  q?: string
  category?: string
  active?: 'true' | 'false' | ''
  page?: number
  size?: number
}

export function lookupProducts(q: string): Promise<SellableProduct[]> {
  return request<SellableProduct[]>(`/api/products/lookup?q=${encodeURIComponent(q)}`)
}

export function listProducts(filters: ProductFilters): Promise<Page<Product>> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return request<Page<Product>>(`/api/products?${params}`)
}

export function productCategories(): Promise<string[]> {
  return request<string[]>('/api/products/categories')
}

export function createProduct(product: ProductInput): Promise<Product> {
  return request<Product>('/api/products', { method: 'POST', body: product })
}

export function updateProduct(id: number, product: ProductInput): Promise<Product> {
  return request<Product>(`/api/products/${id}`, { method: 'PUT', body: product })
}

export const VAT_LABELS: Record<VatCategory, string> = {
  STANDARD: 'VAT 16% (standard)',
  ZERO_RATED: 'Zero-rated (0%)',
  EXEMPT: 'Exempt',
}

export const SERIAL_LABELS: Record<SerialType, string> = {
  NONE: 'No',
  IMEI: 'IMEI (phones)',
  SERIAL: 'Serial number',
}
