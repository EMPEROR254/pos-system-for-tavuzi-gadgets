// Social media (owner and manager): link the shop's Facebook, Instagram,
// TikTok and WhatsApp, write a post with a picture, and post it to the chosen
// accounts. Linking is for the owner (MANAGE_SETTINGS); posting for both.
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useSearchParams } from 'react-router'
import { ListError, ListSkeleton } from '../../components/ListStates'
import { listAccounts, NETWORK_LABELS, type SocialNetwork } from '../../services/social'
import { useSession } from '../../services/session'
import { can } from '../../utils/permissions'
import { toast } from '../../utils/toast'
import { PostComposer } from './PostComposer'
import { PostList } from './PostList'
import { SocialAccounts } from './SocialAccounts'

export default function SocialPage() {
  const user = useSession((s) => s.user)
  const [params, setParams] = useSearchParams()
  const accounts = useQuery({ queryKey: ['social', 'accounts'], queryFn: listAccounts })

  // Back from Facebook/TikTok: say how linking went, then tidy the address.
  useEffect(() => {
    const linked = params.get('linked')
    const error = params.get('error')
    if (!linked && !error) return
    if (linked) {
      const names = linked.split(',').map((n) => NETWORK_LABELS[n.toUpperCase() as SocialNetwork] ?? n)
      toast.success(`Linked ${names.join(' and ')}.`)
    }
    if (error) toast.error(error)
    setParams({}, { replace: true })
  }, [params, setParams])

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Social media</h1>
        <p className="text-sm text-muted">Post a picture with text to the shop&apos;s accounts in one go.</p>
      </div>
      {accounts.isPending ? (
        <ListSkeleton label="Loading accounts" rows={2} />
      ) : accounts.isError ? (
        <ListError what="the accounts" error={accounts.error} onRetry={() => void accounts.refetch()} />
      ) : (
        <>
          <PostComposer accounts={accounts.data} />
          <PostList />
          <SocialAccounts accounts={accounts.data} canManage={can(user, 'MANAGE_SETTINGS')} />
        </>
      )}
    </div>
  )
}
