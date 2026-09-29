// M-Pesa reconciliation: import the M-Pesa statement, then check typed codes (/api/mpesa-reconciliation).
import { request } from './api'
import { toQuery } from './query'

export type ReconciliationResult = 'MATCHED' | 'AMOUNT_DIFFERS' | 'NOT_IN_STATEMENT' | 'NOT_IN_SYSTEM'

export type ReconciliationLine = {
  result: ReconciliationResult
  code: string
  time: string
  systemAmount: number | null
  statementAmount: number | null
  receiptNo: string | null
  saleId: number | null
  cashierName: string | null
  otherParty: string | null
}

export type ReconciliationReport = {
  systemTotal: number
  statementTotal: number
  matched: number
  problems: number
  statementImported: boolean
  lines: ReconciliationLine[]
}

export type ImportResult = {
  rows: number
  added: number
  alreadyImported: number
  from: string | null
  to: string | null
}

export const importStatement = (csv: string) =>
  request<ImportResult>('/api/mpesa-reconciliation/statement', { method: 'POST', text: csv })

export const reconcile = (from: string, to: string) =>
  request<ReconciliationReport>(`/api/mpesa-reconciliation${toQuery({ from, to })}`)
