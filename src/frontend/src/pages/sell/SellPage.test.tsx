import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api'
import * as products from '../../services/products'
import * as sales from '../../services/sales'
import { renderApp } from '../../test/renderApp'
import { cable, phone } from '../../test/fixtures'
import { useCart } from './cartStore'

vi.mock('../../services/products', async (original) => ({
  ...(await original<typeof products>()),
  lookupProducts: vi.fn(),
}))
vi.mock('../../services/sales', async (original) => ({
  ...(await original<typeof sales>()),
  checkout: vi.fn(),
  getReceipt: vi.fn(),
}))

beforeEach(() => {
  useCart.getState().clear()
  vi.mocked(sales.checkout).mockReset()
  vi.mocked(products.lookupProducts).mockImplementation(async (q) =>
    q === 'CBL1' ? [cable] : [phone, cable].filter((p) => p.name.toLowerCase().includes(q.toLowerCase())),
  )
  vi.mocked(sales.getReceipt).mockReturnValue(new Promise(() => {}))
})

describe('SellPage', () => {
  it('adds a scanned barcode and totals the cart', async () => {
    renderApp('/sell', 'CASHIER')
    const search = await screen.findByLabelText('Search products or scan a barcode')
    await userEvent.type(search, 'CBL1{Enter}')
    await userEvent.type(search, 'CBL1{Enter}')

    const cart = await screen.findByRole('list', { name: 'Cart' })
    expect(within(cart).getByText('USB cable')).toBeInTheDocument()
    expect(within(cart).getByLabelText('Quantity of USB cable')).toHaveValue('2')
    expect(screen.getAllByText('KES 400').length).toBeGreaterThan(0)
  })

  it('will not take payment until each phone has its IMEI', async () => {
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'tecno')
    await userEvent.click(await screen.findByRole('option', { name: /Tecno Spark 20/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))
    expect(await screen.findByText('Tecno Spark 20: enter 1 IMEI.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('sells with a split payment and opens the receipt', async () => {
    vi.mocked(sales.checkout).mockResolvedValue({ id: 42, receiptNo: 'TG000042', changeDue: 400 } as sales.Sale)
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'tecno')
    await userEvent.click(await screen.findByRole('option', { name: /Tecno Spark 20/ }))
    await userEvent.type(screen.getByLabelText('IMEI 1 for Tecno Spark 20'), '490154203237518')
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))

    const dialog = await screen.findByRole('dialog')
    const cash = within(dialog).getByLabelText('Cash amount')
    await userEvent.clear(cash)
    await userEvent.type(cash, '2000')
    await userEvent.click(within(dialog).getByRole('button', { name: '+ M-Pesa Till' }))
    await userEvent.type(within(dialog).getByLabelText('M-Pesa code'), 'sjk4h7xyz1')
    const mpesa = within(dialog).getByLabelText('M-Pesa Till amount')
    await userEvent.clear(mpesa)
    await userEvent.type(mpesa, '10000')
    expect(within(dialog).getByText('KES 400')).toBeInTheDocument() // change
    await userEvent.click(within(dialog).getByRole('button', { name: 'Complete sale' }))

    expect(await screen.findByText(/Sale TG000042 saved/)).toBeInTheDocument()
    const request = vi.mocked(sales.checkout).mock.calls[0][0]
    expect(request.lines).toEqual([
      { productId: 1, unitId: 11, quantity: 1, discount: undefined, serials: ['490154203237518'] },
    ])
    expect(request.payments).toEqual([
      { method: 'CASH', amount: 2000, reference: undefined },
      { method: 'MPESA_TILL', amount: 10000, reference: 'SJK4H7XYZ1' },
    ])
    expect(request.clientRef).toBeTruthy()
    expect(useCart.getState().cart.lines).toHaveLength(0)
  })

  it('asks a cashier for supervisor approval when there is a discount, and keeps the cart on errors', async () => {
    vi.mocked(sales.checkout).mockRejectedValue(new ApiError(403, 'Wrong supervisor login.'))
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'CBL1{Enter}')
    await userEvent.type(await screen.findByLabelText('Discount on USB cable (KES)'), '50')
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))

    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Their username'), 'manager')
    await userEvent.type(within(dialog).getByLabelText('Their password'), 'wrong')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Complete sale' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Wrong supervisor login.')
    expect(vi.mocked(sales.checkout).mock.calls[0][0].approval).toEqual({ username: 'manager', password: 'wrong' })
    expect(useCart.getState().cart.lines).toHaveLength(1)
  })
})
