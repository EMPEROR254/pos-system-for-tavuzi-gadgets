// From/to dates with quick choices (today, this week, this month...) for reports.
import { inputClass } from './Field'
import type { DateRange } from '../services/reports'
import { quickRanges } from '../utils/dateRanges'

export function DateRangePicker({ value, onChange }: { value: DateRange; onChange: (range: DateRange) => void }) {
  const quick = quickRanges()
  return (
    <div className="space-y-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-wrap gap-2" role="group" aria-label="Quick dates">
        {quick.map((q) => {
          const active = q.from === value.from && q.to === value.to
          return (
            <button
              key={q.label}
              type="button"
              aria-pressed={active}
              onClick={() => onChange({ from: q.from, to: q.to })}
              className={`min-h-11 rounded-full border px-3 text-sm font-medium transition-colors ${
                active ? 'border-brand bg-brand-soft' : 'border-border text-muted hover:text-foreground'
              }`}
            >
              {q.label}
            </button>
          )
        })}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span className="font-medium">From</span>
          <input
            type="date"
            value={value.from}
            max={value.to}
            onChange={(e) => e.target.value && onChange({ ...value, from: e.target.value })}
            className={inputClass}
          />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">To</span>
          <input
            type="date"
            value={value.to}
            min={value.from}
            onChange={(e) => e.target.value && onChange({ ...value, to: e.target.value })}
            className={inputClass}
          />
        </label>
      </div>
    </div>
  )
}
