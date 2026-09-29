// Dashboard and reports (/api/reports) - owner and manager only.
import { download, request } from './api'
import { toQuery } from './query'

export type DateRange = { from: string; to: string }

export type ColumnKind = 'TEXT' | 'INT' | 'MONEY' | 'PERCENT' | 'DATE' | 'DATETIME'
export type ReportValue = string | number | null
export type ReportColumn = { key: string; label: string; kind: ColumnKind; sum: boolean }
export type ReportFigure = { label: string; value: ReportValue; kind: ColumnKind }

/** One report as a table: the same data is exported to PDF and CSV by the server. */
export type ReportTable = {
  name: string
  title: string
  period: string
  note: string | null
  summary: ReportFigure[]
  columns: ReportColumn[]
  rows: Record<string, ReportValue>[]
  totals: Record<string, ReportValue> | null
}

export const REPORTS = [
  { name: 'daily-sales', label: 'Daily sales' },
  { name: 'product-profit', label: 'Profit per product' },
  { name: 'cashiers', label: 'Sales per cashier' },
  { name: 'shifts', label: 'Shift cash-ups' },
  { name: 'stock-value', label: 'Stock value' },
  { name: 'best-sellers', label: 'Best sellers' },
  { name: 'slow-sellers', label: 'Slow sellers' },
  { name: 'mpesa', label: 'M-Pesa reconciliation' },
  { name: 'vat', label: 'VAT report' },
] as const

export type ReportName = (typeof REPORTS)[number]['name']

export type Slice = { label: string; amount: number }

export type Dashboard = {
  from: string
  to: string
  kpis: {
    netSales: number
    salesCount: number
    averageSale: number
    profit: number
    vat: number
    refunds: number
    cashDifference: number
    lowStock: number
    stockValue: number
    openShifts: number
  }
  days: { day: string; sales: number; profit: number; salesCount: number }[]
  payments: Slice[]
  topProducts: Slice[]
  cashiers: Slice[]
}

export const getDashboard = (range: DateRange) => request<Dashboard>(`/api/reports/dashboard${toQuery(range)}`)

export const getReport = (name: ReportName, range: DateRange) =>
  request<ReportTable>(`/api/reports/${name}${toQuery(range)}`)

export const exportReport = (name: ReportName, range: DateRange, format: 'pdf' | 'csv') =>
  download(
    `/api/reports/${name}/export${toQuery({ ...range, format })}`,
    `tavuzi-${name}-${range.from}-to-${range.to}.${format}`,
  )
