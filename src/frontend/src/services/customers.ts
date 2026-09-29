// Customers (/api/customers): the till finds and adds them; owner/manager manage the list.
import { request } from './api'
import { toQuery } from './query'
import type { SaleSummary } from './sales'
import type { Page } from './users'

export type CustomerBrief = { id: number; name: string; phone: string; marketingOptIn: boolean }

export type Customer = CustomerBrief & {
  email: string | null
  notes: string | null
  visits: number
  totalSpent: number
  lastPurchaseAt: string | null
}

export type CustomerInput = { name: string; phone: string; email?: string; notes?: string; marketingOptIn: boolean }

export const lookupCustomers = (q: string) =>
  request<CustomerBrief[]>(`/api/customers/lookup?q=${encodeURIComponent(q)}`)
export const listCustomers = (f: { q?: string; optedIn?: boolean | ''; page?: number; size?: number }) =>
  request<Page<Customer>>(`/api/customers${toQuery(f)}`)
export const createCustomer = (body: CustomerInput) => request<Customer>('/api/customers', { method: 'POST', body })
export const updateCustomer = (id: number, body: CustomerInput) =>
  request<Customer>(`/api/customers/${id}`, { method: 'PUT', body })
export const customerSales = (id: number, page = 0) =>
  request<Page<SaleSummary>>(`/api/customers/${id}/sales?page=${page}`)

/** "254712345678" → "0712 345 678" for display. */
export function displayPhone(phone: string): string {
  const local = phone.startsWith('254') ? `0${phone.slice(3)}` : phone
  return local.replace(/^(\d{4})(\d{3})(\d{3})$/, '$1 $2 $3')
}
