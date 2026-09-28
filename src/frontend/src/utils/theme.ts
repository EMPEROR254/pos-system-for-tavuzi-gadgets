// Light, dark or system theme. The choice is saved in the browser; "system"
// follows the phone/computer setting, also when it changes. index.html sets
// the theme before the first paint using the same rules.
export type Theme = 'light' | 'dark' | 'system'

const KEY = 'theme'
const systemDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches

export function isDark(theme: Theme, systemPrefersDark: boolean): boolean {
  return theme === 'dark' || (theme === 'system' && systemPrefersDark)
}

export function savedTheme(): Theme {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(theme: Theme): void {
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    // private mode: the theme still applies for this visit
  }
  document.documentElement.classList.toggle('dark', isDark(theme, systemDark()))
}

/** Re-applies "system" when the OS switches; returns a function to stop watching. */
export function followSystemTheme(): () => void {
  const query = window.matchMedia('(prefers-color-scheme: dark)')
  const onChange = () => {
    if (savedTheme() === 'system') applyTheme('system')
  }
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}
