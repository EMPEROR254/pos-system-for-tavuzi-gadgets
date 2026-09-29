// Shows a report value by its column type (money, count, percent, date...).
import type { ColumnKind, ReportValue } from '../services/reports'
import { formatDate, formatDateTime, formatKes } from './format'

export function formatReportValue(value: ReportValue | undefined, kind: ColumnKind): string {
  if (value === null || value === undefined || value === '') return kind === 'TEXT' ? '' : '–'
  switch (kind) {
    case 'MONEY':
      return formatKes(Number(value))
    case 'INT':
      return typeof value === 'number' ? value.toLocaleString('en-KE') : String(value)
    case 'PERCENT':
      return `${Number(value).toFixed(1)}%`
    case 'DATE':
      return formatDate(`${value}T12:00:00+03:00`)
    case 'DATETIME':
      return formatDateTime(String(value))
    default:
      return String(value)
  }
}

export const isNumeric = (kind: ColumnKind) => kind === 'MONEY' || kind === 'INT' || kind === 'PERCENT'

/** Negative money (a cash shortage, a loss) is shown in red. */
export const isNegative = (value: ReportValue | undefined, kind: ColumnKind) =>
  kind === 'MONEY' && typeof value === 'number' && value < 0
