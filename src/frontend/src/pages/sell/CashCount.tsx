// Counting the drawer: how many of each Kenyan note and coin, or just the
// total. Counting by note is slower but catches mistakes.
import { useState } from 'react'
import { inputClass } from '../../components/Field'
import { formatKes } from '../../utils/format'

const NOTES = [1000, 500, 200, 100, 50]
const COINS = [20, 10, 5, 1]

export function CashCount({ onChange }: { onChange: (total: number | null) => void }) {
  const [byNote, setByNote] = useState(true)
  const [counts, setCounts] = useState<Record<number, string>>({})
  const [total, setTotal] = useState('')

  const sum = (next: Record<number, string>) =>
    Object.entries(next).reduce((s, [value, n]) => s + Number(value) * (Number(n) || 0), 0)

  const setCount = (value: number, n: string) => {
    const next = { ...counts, [value]: n.replace(/\D/g, '') }
    setCounts(next)
    onChange(Object.values(next).some((v) => v !== '') ? sum(next) : null)
  }

  const setTyped = (v: string) => {
    setTotal(v)
    const amount = Number(v)
    onChange(v.trim() !== '' && Number.isFinite(amount) && amount >= 0 ? amount : null)
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2 text-sm" role="group" aria-label="How to count">
        {[true, false].map((mode) => (
          <button
            key={String(mode)}
            type="button"
            aria-pressed={byNote === mode}
            onClick={() => {
              setByNote(mode)
              onChange(mode ? (Object.values(counts).some((v) => v) ? sum(counts) : null) : null)
              setTotal('')
            }}
            className={`min-h-11 rounded-full border px-3 font-medium ${
              byNote === mode ? 'border-brand bg-brand-soft' : 'border-border text-muted'
            }`}
          >
            {mode ? 'Count by note' : 'Type the total'}
          </button>
        ))}
      </div>
      {byNote ? (
        <>
          <div className="grid grid-cols-3 gap-2">
            {[...NOTES, ...COINS].map((value) => (
              <label key={value} className="space-y-1 text-sm">
                <span className="text-muted">
                  {value >= 50 ? 'KES' : 'Coin'} {value}
                </span>
                <input
                  inputMode="numeric"
                  aria-label={`Number of KES ${value}`}
                  value={counts[value] ?? ''}
                  onChange={(e) => setCount(value, e.target.value)}
                  className={`${inputClass} tabular-nums`}
                  placeholder="0"
                />
              </label>
            ))}
          </div>
          <p className="text-right text-base font-semibold tabular-nums">Counted: {formatKes(sum(counts))}</p>
        </>
      ) : (
        <label className="block space-y-1 text-sm">
          <span className="font-medium">Cash counted (KES)</span>
          <input inputMode="decimal" value={total} onChange={(e) => setTyped(e.target.value)} className={inputClass} />
        </label>
      )}
    </div>
  )
}
