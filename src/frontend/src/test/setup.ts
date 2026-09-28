// Runs before every test file: DOM matchers and browser bits jsdom lacks.
import '@testing-library/jest-dom/vitest'

window.matchMedia = ((query: string) => ({
  matches: false,
  media: query,
  addEventListener: () => {},
  removeEventListener: () => {},
})) as unknown as typeof window.matchMedia
