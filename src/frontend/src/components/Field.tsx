// A labelled form input with its error message underneath (inline validation).
import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'

export const inputClass =
  'block min-h-11 w-full rounded-xl border border-border bg-background px-3 text-base transition-colors placeholder:text-muted/70 focus:border-brand aria-invalid:border-danger sm:text-sm'

type FieldProps = { label: string; error?: string; hint?: ReactNode }

function FieldFrame({ id, label, error, hint, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-muted">{hint}</p>
      )}
    </div>
  )
}

export const TextField = forwardRef<HTMLInputElement, FieldProps & InputHTMLAttributes<HTMLInputElement>>(
  function TextField({ label, error, hint, className = '', ...input }, ref) {
    const id = useId()
    return (
      <FieldFrame id={id} label={label} error={error} hint={hint}>
        <input
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className={`${inputClass} ${className}`}
          {...input}
        />
      </FieldFrame>
    )
  },
)

export const SelectField = forwardRef<HTMLSelectElement, FieldProps & SelectHTMLAttributes<HTMLSelectElement>>(
  function SelectField({ label, error, hint, className = '', children, ...select }, ref) {
    const id = useId()
    return (
      <FieldFrame id={id} label={label} error={error} hint={hint}>
        <select
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className={`${inputClass} ${className}`}
          {...select}
        >
          {children}
        </select>
      </FieldFrame>
    )
  },
)
