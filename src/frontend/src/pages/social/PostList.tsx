// Recent posts with how each account went: posted, waiting, in the TikTok
// inbox, or failed (with the network's reason and "Try again").
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { ListEmpty, ListError, ListSkeleton, Pager } from '../../components/ListStates'
import { ApiError } from '../../services/api'
import { listPosts, NETWORK_LABELS, retryPost, type SocialPost, type TargetStatus } from '../../services/social'
import { formatDateTime } from '../../utils/format'
import { pictureFromUrl, shareToWhatsApp } from '../../utils/share'
import { toast } from '../../utils/toast'

const STATUS: Record<TargetStatus, { label: string; tone: string }> = {
  POSTED: { label: 'Posted', tone: 'bg-brand-soft' },
  PENDING: { label: 'Posting...', tone: 'bg-background text-muted' },
  SENT_TO_INBOX: { label: 'In TikTok inbox', tone: 'bg-brand-soft' },
  FAILED: { label: 'Failed', tone: 'bg-danger-soft text-danger' },
}

export function PostList() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const posts = useQuery({
    queryKey: ['social', 'posts', page],
    queryFn: () => listPosts(page),
    placeholderData: keepPreviousData,
    // Check again while a post is still being sent.
    refetchInterval: (q) =>
      q.state.data?.content.some((p) => p.targets.some((t) => t.status === 'PENDING')) ? 10_000 : false,
  })

  const retry = async (post: SocialPost, network: SocialPost['targets'][number]['network']) => {
    try {
      await retryPost(post.id, network)
      toast.success(`Sending to ${NETWORK_LABELS[network]} again.`)
      await queryClient.invalidateQueries({ queryKey: ['social', 'posts'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not try again.')
    }
  }

  const share = async (post: SocialPost) => {
    const result = await shareToWhatsApp(post.caption, await pictureFromUrl(post.imageUrl))
    if (result === 'text-only') toast.success('WhatsApp opened with the text - attach the picture there.')
  }

  return (
    <section className="space-y-3">
      <h2 className="font-semibold">Recent posts</h2>
      {posts.isPending ? (
        <ListSkeleton label="Loading posts" rows={2} />
      ) : posts.isError ? (
        <ListError what="posts" error={posts.error} onRetry={() => void posts.refetch()} />
      ) : posts.data.content.length === 0 ? (
        <ListEmpty title="No posts yet">Your posts and how they went will show here.</ListEmpty>
      ) : (
        <>
          <ul className="space-y-3">
            {posts.data.content.map((p) => (
              <li
                key={p.id}
                className="flex flex-col gap-4 rounded-2xl border border-border bg-card p-4 shadow-sm sm:flex-row"
              >
                <img
                  src={p.imageUrl}
                  alt=""
                  loading="lazy"
                  className="size-24 shrink-0 rounded-xl border border-border object-cover"
                />
                <div className="min-w-0 flex-1 space-y-2 text-sm">
                  <div>
                    <p className="font-semibold">{p.title}</p>
                    {p.description && <p className="line-clamp-2 text-muted">{p.description}</p>}
                    <p className="text-xs text-muted">
                      {formatDateTime(p.createdAt)}
                      {p.createdByName && ` · ${p.createdByName}`}
                    </p>
                  </div>
                  <ul className="flex flex-wrap gap-2" aria-label="Where it was posted">
                    {p.targets.map((t) => (
                      <li key={t.network} className="flex items-center gap-2">
                        <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS[t.status].tone}`}>
                          {NETWORK_LABELS[t.network]}: {STATUS[t.status].label}
                        </span>
                        {t.status === 'FAILED' && (
                          <Button variant="secondary" onClick={() => void retry(p, t.network)}>
                            Try again
                          </Button>
                        )}
                      </li>
                    ))}
                  </ul>
                  {p.targets
                    .filter((t) => t.status === 'FAILED' && t.error)
                    .map((t) => (
                      <p key={t.network} className="text-xs text-danger">
                        {t.error}
                      </p>
                    ))}
                  {p.targets.some((t) => t.status === 'SENT_TO_INBOX') && (
                    <p className="text-xs text-muted">Open TikTok, add a sound and publish it from your inbox.</p>
                  )}
                </div>
                <div>
                  <Button variant="secondary" onClick={() => void share(p)}>
                    Share to WhatsApp
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <Pager
            page={posts.data.page}
            totalPages={posts.data.totalPages}
            total={posts.data.totalElements}
            noun="posts"
            onPage={setPage}
          />
        </>
      )}
    </section>
  )
}
