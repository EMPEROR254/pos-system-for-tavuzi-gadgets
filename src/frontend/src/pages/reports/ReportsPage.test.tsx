import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reports from '../../services/reports'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/reports', async (original) => ({
  ...(await original<typeof reports>()),
  getReport: vi.fn(),
  exportReport: vi.fn(),
}))

const vat: reports.ReportTable = {
  name: 'vat',
  title: 'VAT report',
  period: '15 Mar 2027',
  note: 'Prices include VAT.',
  summary: [{ label: 'VAT payable', value: 160, kind: 'MONEY' }],
  columns: [
    { key: 'vat_class', label: 'VAT class', kind: 'TEXT', sum: false },
    { key: 'sales_vat', label: 'VAT on sales', kind: 'MONEY', sum: true },
    { key: 'vat_payable', label: 'VAT payable', kind: 'MONEY', sum: true },
  ],
  rows: [
    { vat_class: 'Standard rated 16%', sales_vat: 320, vat_payable: 160 },
    { vat_class: 'Exempt', sales_vat: 0, vat_payable: 0 },
  ],
  totals: { vat_class: 'Total', sales_vat: 320, vat_payable: 160 },
}

beforeEach(() => {
  vi.mocked(reports.getReport).mockReset().mockResolvedValue(vat)
  vi.mocked(reports.exportReport).mockReset().mockResolvedValue(undefined)
})

describe('ReportsPage', () => {
  it('shows the chosen report for the dates in the link, with its totals', async () => {
    renderApp('/reports?report=vat&from=2027-03-15&to=2027-03-15', 'OWNER')
    expect(await screen.findByRole('heading', { name: 'VAT report' })).toBeInTheDocument()
    expect(reports.getReport).toHaveBeenCalledWith('vat', { from: '2027-03-15', to: '2027-03-15' })
    // Table on laptops and cards on phones are both in the page; CSS shows one.
    expect(screen.getAllByText('Standard rated 16%')).toHaveLength(2)
    const table = screen.getByRole('table', { name: 'VAT report' })
    expect(table).toHaveTextContent('TotalKES 320KES 160')
    expect(screen.getByText('Prices include VAT.')).toBeInTheDocument()
  })

  it('downloads the report as PDF or CSV for the same dates', async () => {
    renderApp('/reports?report=vat&from=2027-03-01&to=2027-03-15', 'MANAGER')
    await screen.findByRole('heading', { name: 'VAT report' })
    await userEvent.click(screen.getByRole('button', { name: 'Download PDF' }))
    expect(reports.exportReport).toHaveBeenCalledWith('vat', { from: '2027-03-01', to: '2027-03-15' }, 'pdf')
    await userEvent.click(screen.getByRole('button', { name: 'CSV' }))
    expect(reports.exportReport).toHaveBeenLastCalledWith('vat', { from: '2027-03-01', to: '2027-03-15' }, 'csv')
  })

  it('switches report and dates', async () => {
    renderApp('/reports?report=vat&from=2027-03-15&to=2027-03-15', 'OWNER')
    await screen.findByRole('heading', { name: 'VAT report' })
    await userEvent.click(screen.getByRole('button', { name: 'Profit per product' }))
    expect(reports.getReport).toHaveBeenLastCalledWith('product-profit', { from: '2027-03-15', to: '2027-03-15' })
    await userEvent.click(screen.getByRole('button', { name: 'Today' }))
    const lastRange = vi.mocked(reports.getReport).mock.lastCall![1]
    expect(lastRange.from).toBe(lastRange.to)
  })

  it('says so when nothing happened in the dates', async () => {
    vi.mocked(reports.getReport).mockResolvedValue({ ...vat, rows: [], totals: null })
    renderApp('/reports?report=vat', 'OWNER')
    expect(await screen.findByText('Nothing in these dates')).toBeInTheDocument()
  })

  it('is not for cashiers', async () => {
    renderApp('/reports', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})
