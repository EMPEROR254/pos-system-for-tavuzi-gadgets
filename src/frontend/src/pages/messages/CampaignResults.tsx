// Recent promotions with how many reached the phone, and the replies customers
// sent back (STOP replies take them off promotions automatically).
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ListEmpty, ListError, ListSkeleton, Pager } from '../../components/ListStates'
import { displayPhone } from '../../services/customers'
import { listCampaigns, listReplies } from '../../services/messages'
import { formatDateTime } from '../../utils/format'

export function CampaignResults() {
  const campaigns = useQuery({ queryKey: ['messages', 'campaigns'], queryFn: listCampaigns })

  return (
    <section className="space-y-3">
      <h2 className="font-semibold">Promotions</h2>
      {campaigns.isPending ? (
        <ListSkeleton label="Loading promotions" rows={2} />
      ) : campaigns.isError ? (
        <ListError what="promotions" error={campaigns.error} onRetry={() => void campaigns.refetch()} />
      ) : campaigns.data.length === 0 ? (
        <ListEmpty title="No promotions sent yet" />
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
          {campaigns.data.map((c) => (
            <li key={c.id} className="space-y-2 px-4 py-3 text-sm">
              <p className="line-clamp-2">{c.message}</p>
              <p className="text-xs text-muted">
                {formatDateTime(c.sendAt ?? c.createdAt)} · {c.audience}
              </p>
              <dl className="grid grid-cols-4 gap-2 text-center tabular-nums">
                <Figure label="To" value={c.recipients} />
                <Figure label="Sent" value={c.sent} />
                <Figure label="Delivered" value={c.delivered} />
                <Figure label="Failed" value={c.failed} danger={c.failed > 0} />
              </dl>
              {c.waiting > 0 && <p className="text-xs text-muted">{c.waiting} still waiting to go out.</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Figure({ label, value, danger }: { label: string; value: number; danger?: boolean }) {
  return (
    <div className="rounded-lg bg-background px-2 py-1">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className={`font-semibold ${danger ? 'text-danger' : ''}`}>{value}</dd>
    </div>
  )
}

export function Replies() {
  const [page, setPage] = useState(0)
  const replies = useQuery({
    queryKey: ['messages', 'replies', page],
    queryFn: () => listReplies(page),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="space-y-3">
      <h2 className="font-semibold">Replies from customers</h2>
      {replies.isPending ? (
        <ListSkeleton label="Loading replies" rows={2} />
      ) : replies.isError ? (
        <ListError what="replies" error={replies.error} onRetry={() => void replies.refetch()} />
      ) : replies.data.content.length === 0 ? (
        <ListEmpty title="No replies yet">Customers who reply to an SMS show up here.</ListEmpty>
      ) : (
        <>
          <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            {replies.data.content.map((r) => (
              <li key={r.id} className="space-y-1 px-4 py-3 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <p className="font-medium">{displayPhone(r.phone)}</p>
                  {r.optedOut && (
                    <span className="rounded-full bg-danger-soft px-2 py-0.5 text-xs font-medium text-danger">
                      Opted out
                    </span>
                  )}
                </div>
                <p>{r.text}</p>
                <p className="text-xs text-muted">{formatDateTime(r.receivedAt)}</p>
              </li>
            ))}
          </ul>
          <Pager
            page={replies.data.page}
            totalPages={replies.data.totalPages}
            total={replies.data.totalElements}
            noun="replies"
            onPage={setPage}
          />
        </>
      )}
    </section>
  )
}
