// Light / Dark / System switch for the header.
import { useEffect, useState } from 'react'
import { applyTheme, followSystemTheme, savedTheme, type Theme } from '../utils/theme'

const OPTIONS: { value: Theme; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
]

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(savedTheme)
  useEffect(() => followSystemTheme(), [])

  return (
    <div
      role="radiogroup"
      aria-label="Theme"
      className="inline-flex rounded-full border border-border bg-card p-0.5 text-xs"
    >
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={theme === option.value}
          onClick={() => {
            setTheme(option.value)
            applyTheme(option.value)
          }}
          className={`rounded-full px-3 py-1 transition-colors duration-200 ${
            theme === option.value ? 'bg-brand text-brand-foreground' : 'text-muted hover:text-foreground'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
