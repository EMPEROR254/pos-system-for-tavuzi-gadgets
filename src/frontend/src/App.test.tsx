import { fireEvent, render, screen } from '@testing-library/react'
import { beforeAll, describe, expect, it } from 'vitest'
import { App } from './App'
import { isDark } from './utils/theme'

beforeAll(() => {
  // jsdom has no matchMedia.
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia
})

describe('App', () => {
  it('shows the project name', () => {
    render(<App />)
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('POS System for Tavuzi Gadgets frontend')
  })

  it('switches to the dark theme and remembers it', () => {
    render(<App />)
    fireEvent.click(screen.getAllByRole('radio', { name: 'Dark' })[0])
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
