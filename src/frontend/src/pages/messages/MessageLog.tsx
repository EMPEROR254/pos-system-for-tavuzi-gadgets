// Every SMS sent (receipts and promotions) with whether it reached the phone,
// and a "Send again" button for ones that failed or were not delivered.
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ListEmpty, ListError, ListSkeleton, Pager } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { displayPhone } from '../../services/customers'
import { canResend, listMessages, resendMessage, type SmsMessage } from '../../services/messages'
import { formatDateTime } from '../../utils/format'
import { toast } from '../../utils/toast'

/** What the owner needs to know about one SMS, in plain words. */
function state(m: SmsMessage): { label: string; tone: string } {
  if (m.status === 'FAILED') return { label: 'Failed', tone: 'bg-danger-soft text-danger' }
  if (m.status === 'QUEUED') return { label: m.sendAfter ? 'Scheduled' : 'Waiting', tone: 'bg-background text-muted' }
  if (m.deliveryStatus === 'Success') return { label: 'Delivered', tone: 'bg-brand-soft' }
  if (canResend(m)) return { label: 'Not delivered', tone: 'bg-danger-soft text-danger' }
  return { label: 'Sent', tone: 'bg-brand-soft' }
}

export function MessageLog() {
  const queryClient = useQueryClient()
  const [kind, setKind] = useState('')
  const [phoneInput, setPhoneInput] = useState('')
  const [phone, setPhone] = useState('')
  const [page, setPage] = useState(0)
  const [resending, setResending] = useState<number | null>(null)

  // Search once typing stops, and only with a full number (the server matches whole numbers).
  useEffect(() => {
    const digits = phoneInput.replace(/\D/g, '')
    const t = setTimeout(() => {
      setPhone(digits.length >= 10 ? phoneInput.trim() : '')
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [phoneInput])

  const filters = { kind, phone, page, size: 20 }
  const log = useQuery({
    queryKey: ['messages', filters],
    queryFn: () => listMessages(filters),
    placeholderData: keepPreviousData,
  })

  const resend = async (id: number) => {
    setResending(id)
    try {
      await resendMessage(id)
      toast.success('SMS will be sent again.')
      await queryClient.invalidateQueries({ queryKey: ['messages'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not send it again.')
    } finally {
      setResending(null)
    }
  }

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold">Sent messages</h2>
        <div className="flex flex-wrap gap-2">
          <input
            type="search"
            aria-label="Phone number"
            placeholder="Phone (07XX XXX XXX)"
            inputMode="tel"
            value={phoneInput}
            onChange={(e) => setPhoneInput(e.target.value)}
            className={`${inputClass} w-44`}
          />
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
            {log.data.content.map((m) => {
              const s = state(m)
              return (
                <li key={m.id} className="space-y-1 px-4 py-3 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <p className="font-medium">
                      {displayPhone(m.phone)} ·{' '}
                      {m.kind === 'PROMO' ? 'Promotion' : m.kind === 'RECEIPT' ? 'Receipt' : 'Test'}
                    </p>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${s.tone}`}>{s.label}</span>
                  </div>
                  <p className="text-muted">{m.body}</p>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-xs text-muted">
                      {formatDateTime(m.createdAt)}
                      {m.status === 'QUEUED' && m.sendAfter && ` · goes out ${formatDateTime(m.sendAfter)}`}
                      {m.error && ` · ${m.error}`}
                      {m.failureReason && ` · ${m.failureReason}`}
                    </p>
                    {canResend(m) && (
                      <Button variant="secondary" busy={resending === m.id} onClick={() => void resend(m.id)}>
                        Send again
                      </Button>
                    )}
                  </div>
                </li>
              )
            })}
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
