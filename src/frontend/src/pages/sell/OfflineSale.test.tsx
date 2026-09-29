import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api'
import { useOffline } from '../../services/offline'
import * as products from '../../services/products'
import * as sales from '../../services/sales'
import { cable } from '../../test/fixtures'
import { renderApp } from '../../test/renderApp'
import { useCart } from './cartStore'

vi.mock('../../services/products', async (original) => ({
  ...(await original<typeof products>()),
  lookupProducts: vi.fn(),
}))
vi.mock('../../services/sales', async (original) => ({
  ...(await original<typeof sales>()),
  checkout: vi.fn(),
}))

const offline = new ApiError(0, 'Cannot reach the server. Check the network and try again.')

beforeEach(() => {
  useCart.getState().clear()
  useOffline.setState({
    waiting: [],
    syncing: false,
    catalogue: { generatedAt: '2026-09-28T07:00:00Z', products: [cable], serialsInStock: {} },
  })
  vi.mocked(products.lookupProducts).mockRejectedValue(offline)
  vi.mocked(sales.checkout).mockReset().mockRejectedValue(offline)
})

describe('Selling while the server is unreachable', () => {
  it('finds products in the saved catalogue and keeps the sale on the till', async () => {
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'CBL1{Enter}')
    expect(await within(screen.getByRole('list', { name: 'Cart' })).findByText('USB cable')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Complete sale' }))

    // A receipt the cashier can print now, while the sale waits to sync.
    const receipt = await screen.findByRole('article', { name: 'Offline receipt' })
    expect(within(receipt).getByText('USB cable')).toBeInTheDocument()
    expect(within(receipt).getByText(/will follow when the till reconnects/)).toBeInTheDocument()
    const [saved] = useOffline.getState().waiting
    expect(saved.total).toBe(200)
    expect(saved.request.offlineCreatedAt).toBeTruthy()
    expect(saved.request.lines).toEqual([{ productId: 2, unitId: 21, quantity: 1, discount: undefined, serials: [] }])
    expect(useCart.getState().cart.lines).toHaveLength(0)
    expect(screen.getByRole('link', { name: '1 waiting to sync' })).toHaveAttribute('href', '/sync')
  })

  it('never stores a supervisor password: discounts needing approval wait for the server', async () => {
    renderApp('/sell', 'CASHIER')
    await userEvent.type(await screen.findByLabelText('Search products or scan a barcode'), 'CBL1{Enter}')
    await userEvent.type(await screen.findByLabelText('Discount on USB cable (KES)'), '20')
    await userEvent.click(screen.getByRole('button', { name: 'Pay' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Their username'), 'manager')
    await userEvent.type(within(dialog).getByLabelText('Their password'), 'secret')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Complete sale' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Discount approval needs the server')
    expect(useOffline.getState().waiting).toEqual([])
    expect(localStorage.getItem('tavuzi-offline') ?? '').not.toContain('secret')
  })
})
