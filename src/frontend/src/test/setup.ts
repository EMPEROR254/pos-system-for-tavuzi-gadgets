// Runs before every test file: DOM matchers and browser bits jsdom lacks.
import '@testing-library/jest-dom/vitest'
import { beforeEach } from 'vitest'
import { useToasts } from '../utils/toast'

window.matchMedia = ((query: string) => ({
  matches: false,
  media: query,
  addEventListener: () => {},
  removeEventListener: () => {},
})) as unknown as typeof window.matchMedia

// Toasts live in a global store: start every test without leftovers.
beforeEach(() => useToasts.setState({ toasts: [] }))
