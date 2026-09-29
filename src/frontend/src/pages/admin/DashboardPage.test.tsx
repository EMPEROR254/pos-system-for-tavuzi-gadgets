import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reports from '../../services/reports'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/reports', async (original) => ({
  ...(await original<typeof reports>()),
  getDashboard: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(reports.getDashboard).mockResolvedValue({
    from: '2027-03-15',
    to: '2027-03-17',
    kpis: {
      netSales: 1660,
      salesCount: 2,
      averageSale: 1410,
      profit: 700,
      vat: 160,
      refunds: 1160,
      cashDifference: -20,
      lowStock: 3,
      stockValue: 40000,
      openShifts: 1,
    },
    days: [
      { day: '2027-03-15', sales: 1660, profit: 700, salesCount: 2 },
      { day: '2027-03-16', sales: 0, profit: 0, salesCount: 0 },
    ],
    payments: [
      { label: 'Cash', amount: 2320 },
      { label: 'M-Pesa', amount: 500 },
      { label: 'Card', amount: 0 },
    ],
    topProducts: [{ label: 'Earbuds', amount: 1160 }],
    cashiers: [{ label: 'Mary', amount: 2820 }],
  })
})

describe('DashboardPage', () => {
  it('shows the key numbers, with a cash shortage in red', async () => {
    renderApp('/dashboard', 'OWNER')
    expect(await screen.findByText('KES 1,660')).toBeInTheDocument()
    expect(screen.getByText('KES 700')).toBeInTheDocument()
    expect(screen.getByText('KES -20')).toHaveClass('text-danger')
    expect(screen.getByText('3 products low on stock')).toBeInTheDocument()
    expect(screen.getByText('Sales and profit per day')).toBeInTheDocument()
    expect(screen.getByText('How customers paid')).toBeInTheDocument()
    // Opens on this week.
    const range = vi.mocked(reports.getDashboard).mock.calls[0][0]
    expect(range.from <= range.to).toBe(true)
  })

  it('is only for owners and managers', async () => {
    renderApp('/dashboard', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})
