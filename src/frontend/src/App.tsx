// Root component: the app frame (header with the theme switch) and the
// current screen. Screens live in src/pages, shared UI in src/components,
// API calls in src/services, helpers in src/utils.
import { ThemeToggle } from './components/ThemeToggle'
import { HomePage } from './pages/HomePage'

export function App() {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-border bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
          <h1 className="text-lg font-semibold tracking-tight">{'POS System for Tavuzi Gadgets frontend'}</h1>
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto max-w-5xl animate-fade-in p-6">
        <HomePage />
      </main>
    </div>
  )
}
