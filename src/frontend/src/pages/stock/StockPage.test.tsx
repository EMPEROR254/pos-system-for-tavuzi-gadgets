import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as stock from '../../services/stock'
import { renderApp } from '../../test/renderApp'
import { countIsDue } from '../../utils/stock'
import { deliveryTotal } from './DeliveryPage'

vi.mock('../../services/stock', async (original) => ({
  ...(await original<typeof stock>()),
  listStock: vi.fn(),
  stockSummary: vi.fn(),
  adjustStock: vi.fn(),
  serialsInStock: vi.fn(),
}))

const part: stock.StockRow = {
  productId: 5,
  name: 'Screen part',
  sku: null,
  category: 'Parts',
  serialType: 'NONE',
  baseUnit: 'Piece',
  stockOnHand: 2,
  reorderLevel: 3,
  low: true,
  costPrice: 1500,
  stockValue: 3000,
}

beforeEach(() => {
  vi.mocked(stock.listStock).mockResolvedValue({ content: [part], page: 0, size: 25, totalElements: 1, totalPages: 1 })
  vi.mocked(stock.stockSummary).mockResolvedValue({ lowStockCount: 1, stockValue: 3000, lastCountAt: null })
  vi.mocked(stock.adjustStock).mockReset()
})

describe('StockPage', () => {
  it('shows stock, the low-stock alert and that a count is due', async () => {
    renderApp('/stock', 'MANAGER')
    expect(await screen.findByText('Screen part')).toBeInTheDocument()
    expect(screen.getByText('Low')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Count due →' })).toHaveAttribute('href', '/stock/count')
  })

  it('records spare parts used for a warranty repair', async () => {
    vi.mocked(stock.adjustStock).mockResolvedValue({ stockOnHand: 1 })
    renderApp('/stock', 'OWNER')
    await userEvent.click(await screen.findByRole('button', { name: 'Adjust stock of Screen part' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Remove stock' }))
    await userEvent.selectOptions(within(dialog).getByLabelText('Reason'), 'WARRANTY_REPAIR')
    await userEvent.type(within(dialog).getByLabelText('Note (optional)'), 'TG000123')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Screen part: now 1 in stock.')).toBeInTheDocument()
    expect(stock.adjustStock).toHaveBeenCalledWith({
      productId: 5,
      quantity: -1,
      reason: 'WARRANTY_REPAIR',
      note: 'TG000123',
      serials: undefined,
    })
  })

  it('is not for cashiers', async () => {
    renderApp('/stock', 'CASHIER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })
})

describe('stock rules', () => {
  it('asks for a count once a week', () => {
    const now = new Date('2026-09-28T07:00:00Z')
    expect(countIsDue(null, now)).toBe(true)
    expect(countIsDue('2026-09-25T07:00:00Z', now)).toBe(false)
    expect(countIsDue('2026-09-20T07:00:00Z', now)).toBe(true)
  })

  it('totals a delivery', () => {
    const line = (quantity: string, unitCost: string) => ({
      key: 1,
      product: {} as never,
      unitId: 1,
      quantity,
      unitCost,
      serials: '',
    })
    expect(deliveryTotal([line('2', '12000'), line('3', '99.99')])).toBe(24299.97)
  })
})
