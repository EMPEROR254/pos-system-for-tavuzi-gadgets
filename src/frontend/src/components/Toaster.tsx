// Shows the toasts from utils/toast.ts (bottom on phones, bottom-right on laptops).
import { useToasts } from '../utils/toast'

export function Toaster() {
  const { toasts, dismiss } = useToasts()
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-50 print:hidden flex flex-col items-center gap-2 p-4 sm:items-end"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className={`pointer-events-auto flex w-full max-w-sm animate-slide-up items-start gap-3 rounded-xl border bg-card p-4 text-sm shadow-lg ${
            t.kind === 'success' ? 'border-success/40' : 'border-danger/40'
          }`}
        >
          <span aria-hidden className={`mt-0.5 font-bold ${t.kind === 'success' ? 'text-success' : 'text-danger'}`}>
            {t.kind === 'success' ? '✓' : '!'}
          </span>
          <p className="flex-1">{t.message}</p>
          <button
            type="button"
            onClick={() => dismiss(t.id)}
            aria-label="Dismiss"
            className="-m-2 rounded-lg p-2 text-muted hover:text-foreground"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}
