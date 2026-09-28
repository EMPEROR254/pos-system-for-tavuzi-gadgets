// The app's button: 44 px tall (easy to tap), shows progress and can't be
// double-submitted while busy.
import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand text-brand-foreground hover:opacity-90 shadow-sm',
  secondary: 'border border-border bg-card hover:bg-background',
  danger: 'bg-danger text-white hover:opacity-90',
  ghost: 'text-muted hover:bg-background hover:text-foreground',
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean }

export function Button({ variant = 'primary', busy = false, disabled, className = '', children, ...rest }: Props) {
  return (
    <button
      type="button"
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 text-sm font-medium transition duration-150 ease-out active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transform-none ${VARIANTS[variant]} ${className}`}
      {...rest}
    >
      {busy && (
        <span
          aria-hidden
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none"
        />
      )}
      {children}
    </button>
  )
}
