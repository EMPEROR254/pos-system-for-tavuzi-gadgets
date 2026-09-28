import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './api'
import { useSession, type CurrentUser } from './session'

const owner: CurrentUser = { id: 1, username: 'owner', fullName: 'Owner', role: 'OWNER', permissions: ['MANAGE_USERS'] }

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  useSession.setState({ status: 'signedIn', accessToken: 'old-token', user: owner })
})
afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

const authHeader = (call: number) => (fetchMock.mock.calls[call][1]?.headers as Record<string, string>).Authorization

describe('request', () => {
  it('sends the access token', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }))
    await expect(request('/api/things')).resolves.toEqual({ ok: true })
    expect(authHeader(0)).toBe('Bearer old-token')
  })

  it('refreshes an expired token once and retries', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Please sign in.' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'new-token', expiresInSeconds: 900, user: owner }))
      .mockResolvedValueOnce(json(200, { ok: true }))

    await expect(request('/api/things')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh')
    expect(authHeader(2)).toBe('Bearer new-token')
    expect(useSession.getState().accessToken).toBe('new-token')
  })

  it('signs out when the refresh fails too', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { detail: 'Please sign in.' }))
      .mockResolvedValueOnce(json(401, { detail: 'Your session has ended.' }))

    await expect(request('/api/things')).rejects.toMatchObject({ status: 401 })
    expect(useSession.getState().status).toBe('signedOut')
  })

  it('turns ProblemDetail responses into readable errors with field messages', async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, { detail: 'Please check the highlighted fields.', errors: { username: 'Too short' } }),
    )
    const error = await request<never>('/api/users', { method: 'POST', body: {} }).catch((e: ApiError) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toBe('Please check the highlighted fields.')
    expect(error.fieldErrors).toEqual({ username: 'Too short' })
  })

  it('explains when the server cannot be reached', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(request('/api/things')).rejects.toThrow('Cannot reach the server')
  })
})
