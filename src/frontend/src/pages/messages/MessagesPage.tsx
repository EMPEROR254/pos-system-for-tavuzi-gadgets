// SMS (owner and manager): send a promotion to customers who agreed to receive
// them, and see every SMS sent (receipts and promotions) with its status.
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass, TextField } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { displayPhone } from '../../services/customers'
import {
  listMessages,
  previewCampaign,
  sendCampaign,
  type CampaignInput,
  type SmsMessage,
} from '../../services/messages'
import { productCategories } from '../../services/products'
import { formatDateTime } from '../../utils/format'
import { toast } from '../../utils/toast'

const STATUS_TONE: Record<SmsMessage['status'], string> = {
  SENT: 'bg-brand-soft',
  QUEUED: 'bg-background text-muted',
  FAILED: 'bg-danger-soft text-danger',
}

export default function MessagesPage() {
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold tracking-tight">SMS messages</h1>
      <PromotionForm />
      <MessageLog />
    </div>
  )
}

function PromotionForm() {
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: productCategories })
  const [message, setMessage] = useState('')
  const [days, setDays] = useState('')
  const [category, setCategory] = useState('')
  const [preview, setPreview] = useState<{ recipients: number; fullMessage: string; smsParts: number } | null>(null)
  const [busy, setBusy] = useState<'preview' | 'send' | null>(null)

  const input = (): CampaignInput => ({
    message: message.trim(),
    boughtWithinDays: days ? Number(days) : undefined,
    category: category || undefined,
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
        toast.success(`Promotion queued for ${result.recipients} customers.`)
        setMessage('')
        setPreview(null)
        await queryClient.invalidateQueries({ queryKey: ['messages'] })
      }
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not do that.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div>
        <h2 className="font-semibold">Send a promotion</h2>
        <p className="text-sm text-muted">
          Goes only to customers who agreed to promotional SMS. An opt-out line is added.
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
          onChange={(e) => {
            setMessage(e.target.value)
            setPreview(null)
          }}
          placeholder="e.g. New Samsung A15 in stock - KES 18,999 this week at Resma Plaza G-13!"
          className="block w-full rounded-xl border border-border bg-background p-3 text-base sm:text-sm"
        />
        <p id="promo-length" className="text-xs text-muted">
          {message.length}/300 characters
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Only customers who bought in the last … days (optional)"
          inputMode="numeric"
          value={days}
          onChange={(e) => {
            setDays(e.target.value)
            setPreview(null)
          }}
        />
        <div className="space-y-1.5 text-sm">
          <label htmlFor="promo-category" className="block font-medium">
            Only customers who bought (optional)
          </label>
          <select
            id="promo-category"
            value={category}
            onChange={(e) => {
              setCategory(e.target.value)
              setPreview(null)
            }}
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
          Send
        </Button>
      </div>
    </section>
  )
}

function MessageLog() {
  const [kind, setKind] = useState('')
  const [page, setPage] = useState(0)
  const filters = { kind, page, size: 20 }
  const log = useQuery({
    queryKey: ['messages', filters],
    queryFn: () => listMessages(filters),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="font-semibold">Sent messages</h2>
        <select
          aria-label="Message type"
          value={kind}
          onChange={(e) => {
            setKind(e.target.value)
            setPage(0)
          }}
          className={`${inputClass} w-auto`}
        >
          <option value="">All</option>
          <option value="RECEIPT">Receipts</option>
          <option value="PROMO">Promotions</option>
        </select>
      </div>
      {log.isPending ? (
        <ListSkeleton label="Loading messages" />
      ) : log.isError ? (
        <ListError what="messages" error={log.error} onRetry={() => void log.refetch()} />
      ) : log.data.content.length === 0 ? (
        <ListEmpty title="No SMS yet">SMS receipts and promotions will appear here.</ListEmpty>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {log.data.content.map((m) => (
              <li key={m.id} className="space-y-1 px-4 py-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <p className="font-medium">
                    {displayPhone(m.phone)} ·{' '}
                    {m.kind === 'PROMO' ? 'Promotion' : m.kind === 'RECEIPT' ? 'Receipt' : 'Test'}
                  </p>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_TONE[m.status]}`}>
                    {m.status === 'SENT' ? 'Sent' : m.status === 'QUEUED' ? 'Waiting' : 'Failed'}
                  </span>
                </div>
                <p className="text-muted">{m.body}</p>
                <p className="text-xs text-muted">
                  {formatDateTime(m.createdAt)}
                  {m.error && ` · ${m.error}`}
                </p>
              </li>
            ))}
          </ul>
          <Pager
            page={log.data.page}
            totalPages={log.data.totalPages}
            total={log.data.totalElements}
            noun="SMS"
            onPage={setPage}
          />
        </>
      )}
    </section>
  )
}
