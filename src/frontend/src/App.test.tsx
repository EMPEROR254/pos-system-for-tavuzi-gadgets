import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from './test/renderApp'
import { isDark } from './utils/theme'

describe('App routes and roles', () => {
  it('sends signed-out visitors to the login page', async () => {
    renderApp('/admin/users')
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows the Staff menu to the owner', async () => {
    renderApp('/', 'OWNER')
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent('Test')
    expect(screen.getAllByRole('link', { name: 'Staff' }).length).toBeGreaterThan(0)
  })

  it('hides Staff from cashiers and blocks the address', async () => {
    renderApp('/', 'CASHIER')
    await screen.findByRole('heading', { level: 1 })
    expect(screen.queryByRole('link', { name: 'Staff' })).not.toBeInTheDocument()
    expect(screen.getByText('Give discounts').closest('li')).toHaveTextContent('(not allowed)')
    expect(screen.getByText('Make sales').closest('li')).toHaveTextContent('(allowed)')
  })

  it('tells a manager the staff screen is not for their role', async () => {
    renderApp('/admin/users', 'MANAGER')
    expect(await screen.findByText('Not available for your role')).toBeInTheDocument()
  })

  it('switches to the dark theme and remembers it', async () => {
    renderApp('/', 'CASHIER')
    fireEvent.click(await screen.findByRole('radio', { name: 'Dark' }))
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('theme')).toBe('dark')
  })
})

describe('isDark', () => {
  it('follows the system only in system mode', () => {
    expect(isDark('system', true)).toBe(true)
    expect(isDark('light', true)).toBe(false)
    expect(isDark('dark', false)).toBe(true)
  })
})
