// The cashier's shift (/api/shifts): open with a float, close with a cash count.
import { request } from './api'

export type CurrentShift = { id: number; openedAt: string; openingFloat: number; salesCount: number }

export type ShiftView = {
  id: number
  cashierName: string
  openedAt: string
  closedAt: string | null
  openingFloat: number
  cashSales: number | null
  cashRefunds: number | null
  expectedCash: number | null
  countedCash: number | null
  difference: number | null
  note: string | null
}

export type CloseResult = { shift: ShiftView; salesCount: number; mpesa: number; card: number }

/** The open shift, or null when there is none (the server answers 204). */
export const currentShift = async () => (await request<CurrentShift | undefined>('/api/shifts/current')) ?? null

export const openShift = (openingFloat: number) =>
  request<CurrentShift>('/api/shifts/open', { method: 'POST', body: { openingFloat } })

export const closeShift = (id: number, countedCash: number, note?: string) =>
  request<CloseResult>(`/api/shifts/${id}/close`, { method: 'POST', body: { countedCash, note } })
