// Write a promotion, see who gets it and what it costs, then send it now or at a
// chosen time. The server holds promotions during quiet hours until the morning.
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass, TextField } from '../../components/Field'
import { ApiError } from '../../services/api'
import { previewCampaign, sendCampaign, type CampaignInput } from '../../services/messages'
import { productCategories } from '../../services/products'
import { formatDateTime, nairobiInputToIso } from '../../utils/format'
import { toast } from '../../utils/toast'

export function PromotionForm({ quietHours }: { quietHours?: string }) {
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: productCategories })
  const [message, setMessage] = useState('')
  const [days, setDays] = useState('')
  const [category, setCategory] = useState('')
  const [sendAt, setSendAt] = useState('')
  const [preview, setPreview] = useState<{ recipients: number; fullMessage: string; smsParts: number } | null>(null)
  const [busy, setBusy] = useState<'preview' | 'send' | null>(null)

  const input = (): CampaignInput => ({
    message: message.trim(),
    boughtWithinDays: days ? Number(days) : undefined,
    category: category || undefined,
    sendAt: sendAt ? nairobiInputToIso(sendAt) : undefined,
  })

  const run = async (action: 'preview' | 'send') => {
    if (!message.trim()) return toast.error('Write the message first.')
    setBusy(action)
    try {
      if (action === 'preview') {
        setPreview(await previewCampaign(input()))
      } else {
        if (!preview || !window.confirm(`Send this SMS to ${preview.recipients} customers?`)) return
        const result = await sendCampaign(input())
        const later = new Date(result.sendsAt).getTime() > Date.now() + 60_000
        toast.success(
          later
            ? `Promotion for ${result.recipients} customers will go out ${formatDateTime(result.sendsAt)}.`
            : `Promotion queued for ${result.recipients} customers.`,
        )
        setMessage('')
        setSendAt('')
        setPreview(null)
        await queryClient.invalidateQueries({ queryKey: ['messages'] })
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not do that.')
    } finally {
      setBusy(null)
    }
  }

  const changed =
    <T,>(set: (v: T) => void) =>
    (v: T) => {
      set(v)
      setPreview(null)
    }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div>
        <h2 className="font-semibold">Send a promotion</h2>
        <p className="text-sm text-muted">
          Goes only to customers who agreed to promotional SMS and never replied STOP. An opt-out line is added.
        </p>
      </div>
      <div className="space-y-1.5 text-sm">
        <label htmlFor="promo-message" className="block font-medium">
          Message
        </label>
        <textarea
          id="promo-message"
          aria-describedby="promo-length"
          rows={3}
          maxLength={300}
          value={message}
          onChange={(e) => changed(setMessage)(e.target.value)}
          placeholder="e.g. New Samsung A15 in stock - KES 18,999 this week at Resma Plaza G-13!"
          className="block w-full rounded-xl border border-border bg-background p-3 text-base sm:text-sm"
        />
        <p id="promo-length" className="text-xs text-muted">
          {message.length}/300 characters
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          label="Only customers who bought in the last … days (optional)"
          inputMode="numeric"
          value={days}
          onChange={(e) => changed(setDays)(e.target.value)}
        />
        <div className="space-y-1.5 text-sm">
          <label htmlFor="promo-category" className="block font-medium">
            Only customers who bought (optional)
          </label>
          <select
            id="promo-category"
            value={category}
            onChange={(e) => changed(setCategory)(e.target.value)}
            className={inputClass}
          >
            <option value="">Anything</option>
            {(categories.data ?? []).map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5 text-sm">
          <label htmlFor="promo-send-at" className="block font-medium">
            Send at (optional)
          </label>
          <input
            id="promo-send-at"
            type="datetime-local"
            aria-describedby="promo-quiet"
            value={sendAt}
            onChange={(e) => changed(setSendAt)(e.target.value)}
            className={inputClass}
          />
          <p id="promo-quiet" className="text-xs text-muted">
            Empty = now. {quietHours && `No promotions ${quietHours} - they wait for the morning.`}
          </p>
        </div>
      </div>
      {preview && (
        <div className="rounded-xl bg-background p-3 text-sm">
          <p>
            <span className="font-semibold">{preview.recipients}</span> customers will receive:
          </p>
          <p className="mt-1 italic">“{preview.fullMessage}”</p>
          <p className="mt-1 text-muted">
            {preview.smsParts} SMS each · about {preview.recipients * preview.smsParts} SMS in total
          </p>
        </div>
      )}
      <div className="flex gap-2">
        <Button variant="secondary" busy={busy === 'preview'} onClick={() => void run('preview')}>
          Preview
        </Button>
        <Button busy={busy === 'send'} disabled={!preview || preview.recipients === 0} onClick={() => void run('send')}>
          {sendAt ? 'Schedule' : 'Send'}
        </Button>
      </div>
    </section>
  )
}
