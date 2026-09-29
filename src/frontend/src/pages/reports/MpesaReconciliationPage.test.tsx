import { fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as etims from '../../services/etims'
import * as reconciliation from '../../services/reconciliation'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/reconciliation', () => ({ importStatement: vi.fn(), reconcile: vi.fn() }))
vi.mock('../../services/etims', () => ({
  etimsSummary: vi.fn(),
  listEtimsInvoices: vi.fn(),
  retryEtimsInvoice: vi.fn(),
}))

beforeEach(() => {
  vi.mocked(reconciliation.reconcile).mockResolvedValue({
    systemTotal: 3000,
    statementTotal: 2300,
    matched: 1,
    problems: 2,
    statementImported: true,
    lines: [
      {
        result: 'NOT_IN_STATEMENT',
        code: 'SGC0000001',
        time: '2026-09-28T07:03:00Z',
        systemAmount: 1500,
        statementAmount: null,
        receiptNo: 'TG000012',
        saleId: 12,
        cashierName: 'Mary',
        otherParty: null,
      },
      {
        result: 'MATCHED',
        code: 'SGA0000001',
        time: '2026-09-28T07:01:00Z',
        systemAmount: 1500,
        statementAmount: 1500,
        receiptNo: 'TG000011',
        saleId: 11,
        cashierName: 'Mary',
        otherParty: 'GRACE',
      },
    ],
  })
})

describe('MpesaReconciliationPage', () => {
  it('flags M-Pesa codes that are not in the statement', async () => {
    renderApp('/reports/mpesa', 'MANAGER')
    expect(await screen.findByText('SGC0000001')).toBeInTheDocument()
    expect(screen.getByText('Not in statement')).toBeInTheDocument()
    expect(screen.getByText(/a typo, or a fake SMS/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'TG000012' })).toHaveAttribute('href', '/sales/12/receipt')
  })

  it('imports the statement file the owner picks', async () => {
    vi.mocked(reconciliation.importStatement).mockResolvedValue({
      rows: 3,
      added: 3,
      alreadyImported: 0,
      from: null,
      to: null,
    })
    const { container } = renderApp('/reports/mpesa', 'OWNER')
    await screen.findByText('SGC0000001')
    const input = container.ownerDocument.getElementById('statement-file') as HTMLInputElement
    const file = new File(['Receipt No.,Completion Time,Paid In\n'], 'statement.csv', { type: 'text/csv' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByText('Statement imported: 3 new payments.')).toBeInTheDocument()
    expect(reconciliation.importStatement).toHaveBeenCalledWith('Receipt No.,Completion Time,Paid In\n')
  })

  it('is not for cashiers', async () => {
    renderApp('/reports/mpesa', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})

describe('EtimsPage', () => {
  it('shows invoices KRA refused, with the reason, and sends them again', async () => {
    vi.mocked(etims.etimsSummary).mockResolvedValue({ mode: 'SANDBOX', pending: 0, sent: 5, rejected: 1 })
    vi.mocked(etims.listEtimsInvoices).mockResolvedValue({
      content: [
        {
          id: 4,
          invoiceNo: 9,
          kind: 'SALE',
          saleId: 12,
          receiptNo: 'TG000012',
          status: 'REJECTED',
          attempts: 1,
          lastError: 'KRA eTIMS 910: Invalid item class code',
          createdAt: '2026-09-28T07:00:00Z',
          sentAt: null,
        },
      ],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(etims.retryEtimsInvoice).mockResolvedValue({} as etims.EtimsInvoice)
    renderApp('/settings/etims', 'OWNER')
    expect(await screen.findByText('KRA eTIMS 910: Invalid item class code')).toBeInTheDocument()
    expect(screen.getByText('KRA test system')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Send again' }))
    expect(await screen.findByText('The invoice will be sent to KRA again shortly.')).toBeInTheDocument()
    expect(etims.retryEtimsInvoice).toHaveBeenCalledWith(4)
  })
})
