// Quick date ranges for reports (today, this week, ...), as days in Nairobi.
// Dates are "YYYY-MM-DD" strings; the arithmetic is done on UTC dates so the
// computer's own time zone can't shift a day.
import { todayInNairobi } from './format'

export type QuickRange = { label: string; from: string; to: string }

const toDate = (day: string) => new Date(`${day}T00:00:00Z`)
const toDay = (date: Date) => date.toISOString().slice(0, 10)

export function addDays(day: string, days: number): string {
  const d = toDate(day)
  d.setUTCDate(d.getUTCDate() + days)
  return toDay(d)
}

export function quickRanges(today: string = todayInNairobi()): QuickRange[] {
  const d = toDate(today)
  // Weeks start on Monday in Kenya.
  const weekStart = addDays(today, -((d.getUTCDay() + 6) % 7))
  const monthStart = `${today.slice(0, 7)}-01`
  const lastMonthEnd = addDays(monthStart, -1)
  return [
    { label: 'Today', from: today, to: today },
    { label: 'Yesterday', from: addDays(today, -1), to: addDays(today, -1) },
    { label: 'This week', from: weekStart, to: today },
    { label: 'This month', from: monthStart, to: today },
    { label: 'Last month', from: `${lastMonthEnd.slice(0, 7)}-01`, to: lastMonthEnd },
    { label: 'Last 30 days', from: addDays(today, -29), to: today },
  ]
}
