import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as products from '../../services/products'
import * as shifts from '../../services/shifts'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/shifts', async (original) => ({
  ...(await original<typeof shifts>()),
  currentShift: vi.fn(),
  openShift: vi.fn(),
  closeShift: vi.fn(),
}))
vi.mock('../../services/products', async (original) => ({
  ...(await original<typeof products>()),
  lookupProducts: vi.fn().mockResolvedValue([]),
}))

const open: shifts.CurrentShift = { id: 7, openedAt: '2027-03-15T05:00:00Z', openingFloat: 1000, salesCount: 2 }

beforeEach(() => {
  vi.mocked(shifts.currentShift).mockReset()
  vi.mocked(shifts.openShift).mockReset()
  vi.mocked(shifts.closeShift).mockReset()
  vi.spyOn(window, 'confirm').mockReturnValue(true)
})

describe('shift on the till', () => {
  it('opens the shift with the float counted', async () => {
    vi.mocked(shifts.currentShift).mockResolvedValue(null)
    vi.mocked(shifts.openShift).mockResolvedValue(open)
    renderApp('/sell', 'CASHIER')

    await userEvent.click(await screen.findByRole('button', { name: 'Open shift' }))
    await userEvent.click(screen.getAllByRole('button', { name: 'Open shift' }).at(-1)!)
    expect(await screen.findByText('Type the float, e.g. 1000.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Float in the drawer (KES)'), '1000')
    await userEvent.click(screen.getAllByRole('button', { name: 'Open shift' }).at(-1)!)
    expect(shifts.openShift).toHaveBeenCalledWith(1000)
    expect(await screen.findByText('Shift opened.')).toBeInTheDocument()
  })

  it('closes the shift with a blind count by note, then shows short or over', async () => {
    vi.mocked(shifts.currentShift).mockResolvedValue(open)
    vi.mocked(shifts.closeShift).mockResolvedValue({
      shift: {
        id: 7,
        cashierName: 'Mary',
        openedAt: open.openedAt,
        closedAt: '2027-03-15T15:00:00Z',
        openingFloat: 1000,
        cashSales: 2320,
        cashRefunds: 1160,
        expectedCash: 2160,
        countedCash: 2100,
        difference: -60,
        note: null,
      },
      salesCount: 2,
      mpesa: 500,
      card: 0,
    })
    renderApp('/sell', 'CASHIER')

    await userEvent.click(await screen.findByRole('button', { name: 'Close shift' }))
    // Nothing tells the cashier what should be in the drawer before counting.
    expect(screen.queryByText(/Expected/)).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Number of KES 1000'), '2')
    await userEvent.type(screen.getByLabelText('Number of KES 100'), '1')
    expect(screen.getByText('Counted: KES 2,100')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Close shift' }).at(-1)!)

    expect(shifts.closeShift).toHaveBeenCalledWith(7, 2100, undefined)
    expect(await screen.findByText('Short by KES 60')).toBeInTheDocument()
    expect(screen.getByText('KES 2,160')).toBeInTheDocument()
  })
})
