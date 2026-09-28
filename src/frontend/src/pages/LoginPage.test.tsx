import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSession } from '../services/session'
import { renderApp, userWithRole } from '../test/renderApp'

const fetchMock = vi.fn<typeof fetch>()
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(() => vi.stubGlobal('fetch', fetchMock))
afterEach(() => {
  fetchMock.mockReset()
  vi.unstubAllGlobals()
})

describe('LoginPage', () => {
  it('shows what is missing next to each field', async () => {
    renderApp('/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your username.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows the server message for a wrong password', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { detail: 'Wrong username or password.' }))
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Username'), 'mary')
    await userEvent.type(screen.getByLabelText('Password'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong username or password.')
    expect(useSession.getState().status).toBe('signedOut')
  })

  it('signs in and opens the page the user asked for', async () => {
    const owner = userWithRole('OWNER')
    fetchMock.mockImplementation(async (input) =>
      String(input) === '/api/auth/login'
        ? json(200, { accessToken: 'abc', expiresInSeconds: 900, user: owner })
        : json(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    )
    renderApp('/login?next=/admin/users')
    await userEvent.type(await screen.findByLabelText('Username'), 'owner')
    await userEvent.type(screen.getByLabelText('Password'), 'owner-pass-123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Staff' })).toBeInTheDocument()
    expect(useSession.getState().accessToken).toBe('abc')
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body))
    expect(body).toEqual({ username: 'owner', password: 'owner-pass-123' })
  })
})
