import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as customers from '../../services/customers'
import * as products from '../../services/products'
import * as sales from '../../services/sales'
import { cable } from '../../test/fixtures'
import { renderApp } from '../../test/renderApp'
import { useCart } from './cartStore'

vi.mock('../../services/customers', async (original) => ({
  ...(await original<typeof customers>()),
  lookupCustomers: vi.fn(),
}))
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
  vi.mocked(sales.checkout).mockClear()
  vi.mocked(products.lookupProducts).mockResolvedValue([cable])
  vi.mocked(sales.getReceipt).mockReturnValue(new Promise(() => {}))
  vi.mocked(sales.checkout).mockResolvedValue({ id: 9, receiptNo: 'TG000009', changeDue: 0 } as sales.Sale)
  vi.mocked(customers.lookupCustomers).mockResolvedValue([
    { id: 77, name: 'Grace Chebet', phone: '254712345678', marketingOptIn: true },
  ])
})

describe('Customer on a sale', () => {
  it('finds a returning customer by phone and sends them an SMS receipt', async () => {
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'CBL1{Enter}')
    await userEvent.click(screen.getByText('Customer (optional)'))
    await userEvent.type(screen.getByLabelText('Customer phone'), '0712345')
    await userEvent.click(await screen.findByRole('button', { name: /Grace Chebet/ }))
    expect(screen.getByText('0712 345 678')).toBeInTheDocument()
    expect(screen.getByLabelText('Send SMS receipt')).toBeChecked()

    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Complete sale' }))

    await screen.findByText(/Sale TG000009 saved/)
    expect(vi.mocked(sales.checkout).mock.calls[0][0]).toMatchObject({
      customerId: 77,
      customerPhone: '254712345678',
      smsReceipt: true,
      marketingOptIn: true,
    })
  })

  it('a new customer only gets promotions after agreeing', async () => {
    vi.mocked(customers.lookupCustomers).mockResolvedValue([])
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'CBL1{Enter}')
    await userEvent.click(screen.getByText('Customer (optional)'))
    await userEvent.type(screen.getByLabelText('Customer phone'), '0722000111')
    await userEvent.type(screen.getByLabelText('Customer name'), 'John Otieno')
    expect(screen.getByLabelText('Customer agrees to promotional SMS')).not.toBeChecked()

    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Complete sale' }))
    await screen.findByText(/Sale TG000009 saved/)
    expect(vi.mocked(sales.checkout).mock.calls[0][0]).toMatchObject({
      customerId: undefined,
      customerName: 'John Otieno',
      customerPhone: '0722000111',
      marketingOptIn: false,
      smsReceipt: true,
    })
  })
})
