import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../services/api'
import * as users from '../../services/users'
import { renderApp } from '../../test/renderApp'

vi.mock('../../services/users')

const mary: users.User = {
  id: 2,
  username: 'mary',
  fullName: 'Mary Wanjiru',
  role: 'CASHIER',
  active: true,
  locked: false,
  createdAt: '2026-09-28T07:00:00Z',
}
const page = (content: users.User[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

beforeEach(() => vi.mocked(users.listUsers).mockReset())

describe('UsersPage', () => {
  it('lists staff with their role and status', async () => {
    vi.mocked(users.listUsers).mockResolvedValue(page([mary]))
    renderApp('/admin/users', 'OWNER')
    const table = await screen.findByRole('table')
    expect(within(table).getByText('Mary Wanjiru')).toBeInTheDocument()
    expect(within(table).getByText('Cashier / front desk')).toBeInTheDocument()
    expect(within(table).getByText('Active')).toBeInTheDocument()
    expect(users.listUsers).toHaveBeenCalledWith(expect.objectContaining({ active: 'true', page: 0, sort: 'fullName' }))
  })

  it('shows an empty state and an error state with retry', async () => {
    vi.mocked(users.listUsers).mockResolvedValueOnce(page([]))
    const { unmount } = renderApp('/admin/users', 'OWNER')
    expect(await screen.findByText('No staff found')).toBeInTheDocument()
    unmount()

    vi.mocked(users.listUsers).mockRejectedValueOnce(new ApiError(0, 'Cannot reach the server.'))
    renderApp('/admin/users', 'OWNER')
    expect(await screen.findByText('Could not load staff.')).toBeInTheDocument()
    vi.mocked(users.listUsers).mockResolvedValueOnce(page([mary]))
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('validates the new staff form inline, then adds the person', async () => {
    vi.mocked(users.listUsers).mockResolvedValue(page([]))
    vi.mocked(users.createUser).mockResolvedValue({ ...mary, id: 3, username: 'john', fullName: 'John Otieno' })
    renderApp('/admin/users', 'OWNER')
    await userEvent.click(await screen.findByRole('button', { name: '+ Add staff member' }))
    const dialog = screen.getByRole('dialog', { name: 'Add staff member' })

    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }))
    expect(await within(dialog).findByText('Enter the full name.')).toBeInTheDocument()
    expect(within(dialog).getByText('Use at least 8 characters.')).toBeInTheDocument()
    expect(users.createUser).not.toHaveBeenCalled()

    await userEvent.type(within(dialog).getByLabelText('Full name'), 'John Otieno')
    await userEvent.type(within(dialog).getByLabelText('Username'), 'john')
    await userEvent.type(within(dialog).getByLabelText('First password'), 'john-pass-1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('John Otieno can now sign in as "john".')).toBeInTheDocument()
    expect(users.createUser).toHaveBeenCalledWith({
      fullName: 'John Otieno',
      username: 'john',
      role: 'CASHIER',
      password: 'john-pass-1',
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('puts a taken username error next to the field', async () => {
    vi.mocked(users.listUsers).mockResolvedValue(page([]))
    vi.mocked(users.createUser).mockRejectedValue(new ApiError(409, 'That username is already taken.'))
    renderApp('/admin/users', 'OWNER')
    await userEvent.click(await screen.findByRole('button', { name: '+ Add staff member' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Full name'), 'Mary Two')
    await userEvent.type(within(dialog).getByLabelText('Username'), 'mary')
    await userEvent.type(within(dialog).getByLabelText('First password'), 'password-1')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }))
    expect(await within(dialog).findByText('That username is already taken.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Username')).toHaveAttribute('aria-invalid', 'true')
  })
})
