import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useOffline, type WaitingSale } from '../../services/offline'
import * as sales from '../../services/sales'
import * as sync from '../../services/sync'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/sync', () => ({ serverWaiting: vi.fn() }))
vi.mock('../../services/sales', async (original) => ({ ...(await original<typeof sales>()), checkout: vi.fn() }))

const refused: WaitingSale = {
  clientRef: 'r1',
  request: { clientRef: 'r1', priceTier: 'RETAIL', lines: [], payments: [] },
  total: 15000,
  itemCount: 1,
  cashierName: 'Mary',
  createdAt: '2026-09-28T07:00:00Z',
  receipt: { lines: [], payments: [], change: 0 },
  problem: '490154203237518 is not in stock for Tecno Spark 20.',
}

beforeEach(() => {
  useOffline.setState({ waiting: [refused], syncing: false, catalogue: null })
  vi.mocked(sync.serverWaiting).mockResolvedValue({
    serverTime: '2026-09-28T07:00:00Z',
    etimsMode: 'SANDBOX',
    etimsPending: 3,
    etimsRejected: 0,
    smsQueued: 2,
  })
})

describe('SyncPage', () => {
  it('shows why the server refused a sale and asks cashiers to get a manager', async () => {
    renderApp('/sync', 'CASHIER')
    expect(await screen.findByText(/not in stock for Tecno Spark 20/)).toBeInTheDocument()
    expect(screen.getByText('Ask a manager to sort this out.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
    expect(await screen.findByText('3')).toBeInTheDocument() // eTIMS invoices waiting on the server
  })

  it('lets a manager send it again once fixed', async () => {
    vi.mocked(sales.checkout).mockResolvedValue({ id: 5 } as sales.Sale)
    renderApp('/sync', 'MANAGER')
    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('Sale sent.')).toBeInTheDocument()
    expect(useOffline.getState().waiting).toEqual([])
  })
})
