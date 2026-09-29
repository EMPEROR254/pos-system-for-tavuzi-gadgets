import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cable, phone } from '../test/fixtures'
import { ApiError } from './api'
import { restoreSession } from './auth'
import { retryWaitingSale, searchOffline, syncWaitingSales, useOffline, type WaitingSale } from './offline'
import * as sales from './sales'
import { useSession } from './session'

vi.mock('./sales', async (original) => ({ ...(await original<typeof sales>()), checkout: vi.fn() }))

const waiting = (clientRef: string): WaitingSale => ({
  clientRef,
  request: { clientRef, priceTier: 'RETAIL', lines: [], payments: [], offlineCreatedAt: '2026-09-28T07:00:00Z' },
  total: 200,
  itemCount: 1,
  cashierName: 'Mary',
  createdAt: '2026-09-28T07:00:00Z',
  receipt: { lines: [], payments: [], change: 0 },
})

beforeEach(() => {
  vi.mocked(sales.checkout).mockReset()
  useOffline.setState({
    waiting: [],
    syncing: false,
    catalogue: {
      generatedAt: '2026-09-28T07:00:00Z',
      products: [phone, cable],
      serialsInStock: { '1': ['490154203237518'] },
    },
  })
})

describe('offline catalogue', () => {
  it('finds a scanned barcode, or products by the words typed', () => {
    expect(searchOffline('CBL1')).toEqual([cable])
    expect(searchOffline('spark tecno')).toEqual([phone])
    expect(searchOffline('nothing like this')).toEqual([])
  })
})

describe('syncing sales made offline', () => {
  it('sends waiting sales and removes them once the server has them', async () => {
    useOffline.getState().add(waiting('a'))
    useOffline.getState().add(waiting('b'))
    vi.mocked(sales.checkout).mockResolvedValue({ id: 1 } as sales.Sale)

    expect(await syncWaitingSales()).toHaveLength(2)
    expect(useOffline.getState().waiting).toEqual([])
    // Sent with the time the sale really happened.
    expect(vi.mocked(sales.checkout).mock.calls[0][0].offlineCreatedAt).toBe('2026-09-28T07:00:00Z')
  })

  it('keeps everything while the server is still unreachable', async () => {
    useOffline.getState().add(waiting('a'))
    useOffline.getState().add(waiting('b'))
    vi.mocked(sales.checkout).mockRejectedValue(new ApiError(0, 'Cannot reach the server.'))

    expect(await syncWaitingSales()).toEqual([])
    expect(useOffline.getState().waiting.map((w) => w.clientRef)).toEqual(['a', 'b'])
    expect(sales.checkout).toHaveBeenCalledTimes(1) // stops at the first failure
  })

  it('keeps a refused sale for a manager and carries on with the rest', async () => {
    useOffline.getState().add(waiting('a'))
    useOffline.getState().add(waiting('b'))
    vi.mocked(sales.checkout)
      .mockRejectedValueOnce(new ApiError(409, '490154203237518 is not in stock.'))
      .mockResolvedValueOnce({ id: 2 } as sales.Sale)

    await syncWaitingSales()
    expect(useOffline.getState().waiting).toEqual([{ ...waiting('a'), problem: '490154203237518 is not in stock.' }])

    // Refused sales are not retried automatically, only when a manager asks.
    vi.mocked(sales.checkout).mockResolvedValue({ id: 3 } as sales.Sale)
    await syncWaitingSales()
    expect(useOffline.getState().waiting).toHaveLength(1)
    expect(await retryWaitingSale('a')).toBe(true)
    expect(useOffline.getState().waiting).toEqual([])
  })
})

describe('starting the app without the server', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    useSession.setState({ status: 'checking', accessToken: null, user: null })
  })

  it('carries on as the last person signed in on this till', async () => {
    useSession
      .getState()
      .signIn('token', { id: 3, username: 'mary', fullName: 'Mary', role: 'CASHIER', permissions: ['MAKE_SALE'] })
    useSession.setState({ status: 'checking', accessToken: null, user: null })
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await restoreSession()
    expect(useSession.getState().status).toBe('signedIn')
    expect(useSession.getState().user?.fullName).toBe('Mary')
    expect(useSession.getState().accessToken).toBeNull()
  })

  it('signs out when the server says the session ended', async () => {
    useSession
      .getState()
      .signIn('token', { id: 3, username: 'mary', fullName: 'Mary', role: 'CASHIER', permissions: ['MAKE_SALE'] })
    fetchMock.mockResolvedValue(new Response('{}', { status: 401 }))
    await restoreSession()
    expect(useSession.getState().status).toBe('signedOut')
    // ...and forgets the saved user, so nobody can carry on offline as them.
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    await restoreSession()
    expect(useSession.getState().status).toBe('signedOut')
  })
})
