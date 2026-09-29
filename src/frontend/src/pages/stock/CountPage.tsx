// Weekly stock count: start a count (everything or one category), type what is
// on the shelf (saved as you go, so it can take several sittings), then finish
// to set stock to what was counted.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListError, ListSkeleton } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { productCategories } from '../../services/products'
import {
  cancelCount,
  finishCount,
  getCount,
  recentCounts,
  saveCount,
  startCount,
  type StockCount,
} from '../../services/stock'
import { formatDate, formatKes } from '../../utils/format'
import { toast } from '../../utils/toast'
import { StockNav } from './StockNav'

export default function CountPage() {
  const counts = useQuery({ queryKey: ['stock', 'counts'], queryFn: recentCounts })
  const open = counts.data?.find((c) => c.status === 'OPEN')

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">Stock</h1>
      <StockNav />
      {counts.isPending ? (
        <ListSkeleton label="Loading counts" />
      ) : counts.isError ? (
        <ListError what="stock counts" error={counts.error} onRetry={() => void counts.refetch()} />
      ) : open ? (
        <OpenCount id={open.id} />
      ) : (
        <StartCount recent={counts.data} />
      )}
    </div>
  )
}

function StartCount({ recent }: { recent: StockCount[] }) {
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: productCategories })
  const [category, setCategory] = useState('')
  const [busy, setBusy] = useState(false)

  const start = async () => {
    setBusy(true)
    try {
      await startCount(category || undefined)
      await queryClient.invalidateQueries({ queryKey: ['stock', 'counts'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not start the count.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="space-y-3 rounded-2xl border border-border bg-card p-5 shadow-sm">
        <h2 className="font-semibold">Start a stock count</h2>
        <p className="text-sm text-muted">
          Count once a week. The till keeps selling while you count - sales made meanwhile are kept.
        </p>
        <div className="flex flex-wrap gap-2">
          <select
            aria-label="What to count"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className={`${inputClass} sm:w-72`}
          >
            <option value="">Everything</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                Only {c}
              </option>
            ))}
          </select>
          <Button busy={busy} onClick={() => void start()}>
            Start count
          </Button>
        </div>
      </div>
      {recent.length > 0 && (
        <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
          <h2 className="font-semibold">Recent counts</h2>
          <ul className="mt-2 divide-y divide-border text-sm">
            {recent.map((c) => (
              <li key={c.id} className="flex justify-between gap-3 py-2">
                <span>
                  {formatDate(c.startedAt)} · {c.category ?? 'Everything'} ·{' '}
                  {c.status === 'FINISHED' ? `${c.countedProducts} of ${c.products} counted` : 'Cancelled'}
                </span>
                <span className={`tabular-nums ${c.differenceValue < 0 ? 'text-danger' : ''}`}>
                  {c.status === 'FINISHED' && formatKes(c.differenceValue)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function OpenCount({ id }: { id: number }) {
  const queryClient = useQueryClient()
  const count = useQuery({ queryKey: ['stock', 'count', id], queryFn: () => getCount(id) })
  const [typed, setTyped] = useState<Record<number, string>>({})
  const [filter, setFilter] = useState('')
  const [busy, setBusy] = useState<'save' | 'finish' | 'cancel' | null>(null)

  if (count.isPending) return <ListSkeleton label="Loading count" rows={6} />
  if (count.isError) return <ListError what="the count" error={count.error} onRetry={() => void count.refetch()} />

  const lines = count.data.lines ?? []
  const value = (productId: number, counted: number | null) =>
    typed[productId] ?? (counted == null ? '' : String(counted))
  const shown = lines.filter((l) => l.name.toLowerCase().includes(filter.toLowerCase()))

  const save = async () => {
    const entries = Object.entries(typed).map(([productId, text]) => ({
      productId: Number(productId),
      counted: text.trim() === '' ? null : Number(text),
    }))
    if (entries.some((e) => e.counted != null && (!Number.isInteger(e.counted) || e.counted < 0))) {
      toast.error('Counts must be whole numbers of 0 or more.')
      return false
    }
    if (entries.length > 0) {
      queryClient.setQueryData(['stock', 'count', id], await saveCount(id, entries))
      setTyped({})
    }
    return true
  }

  const run = async (action: 'save' | 'finish' | 'cancel') => {
    if (action === 'cancel' && !window.confirm('Cancel this count? Nothing will change.')) return
    if (action === 'finish' && !window.confirm('Finish the count and set stock to what was counted?')) return
    setBusy(action)
    try {
      if (action === 'cancel') await cancelCount(id)
      else if (!(await save())) return
      if (action === 'finish') {
        const done = await finishCount(id)
        toast.success(`Count finished. Difference at cost: ${formatKes(done.differenceValue)}.`)
      } else if (action === 'save') {
        toast.success('Counts saved.')
      }
      if (action !== 'save') {
        await queryClient.invalidateQueries({ queryKey: ['stock'] })
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not save.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
        <p className="text-sm">
          Count started {formatDate(count.data.startedAt)} · {count.data.category ?? 'Everything'} ·{' '}
          <span className="font-semibold">
            {count.data.countedProducts} of {count.data.products}
          </span>{' '}
          counted
        </p>
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" busy={busy === 'cancel'} onClick={() => void run('cancel')}>
            Cancel count
          </Button>
          <Button variant="secondary" busy={busy === 'save'} onClick={() => void run('save')}>
            Save
          </Button>
          <Button busy={busy === 'finish'} onClick={() => void run('finish')}>
            Finish count
          </Button>
        </div>
      </div>
      <input
        aria-label="Find a product in the count"
        type="search"
        placeholder="Find a product"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        className={inputClass}
      />
      <p className="text-xs text-muted">
        IMEI/serial products show a difference but are not changed by the count - fix those with Adjust stock, choosing
        the missing IMEIs.
      </p>
      <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
        {shown.map((l) => (
          <li key={l.productId} className="flex items-center gap-3 px-4 py-2">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{l.name}</p>
              <p className="text-xs text-muted">
                System says {l.expected}
                {l.difference != null && l.difference !== 0 && (
                  <span className={l.difference < 0 ? 'text-danger' : 'text-success'}>
                    {' '}
                    · {l.difference > 0 ? '+' : ''}
                    {l.difference}
                  </span>
                )}
              </p>
            </div>
            <input
              aria-label={`Counted ${l.name}`}
              inputMode="numeric"
              placeholder="Count"
              value={value(l.productId, l.counted)}
              onChange={(e) => setTyped({ ...typed, [l.productId]: e.target.value })}
              className={`${inputClass} w-24 text-right tabular-nums`}
            />
          </li>
        ))}
      </ul>
    </div>
  )
}
