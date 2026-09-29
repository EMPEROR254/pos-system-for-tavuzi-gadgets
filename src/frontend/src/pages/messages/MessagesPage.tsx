// SMS (owner and manager): send or schedule a promotion to customers who agreed
// to receive them, see how promotions did, read replies, and see every SMS sent
// (receipts and promotions) with its delivery status.
import { useQuery } from '@tanstack/react-query'
import { smsAccount } from '../../services/messages'
import { CampaignResults, Replies } from './CampaignResults'
import { MessageLog } from './MessageLog'
import { PromotionForm } from './PromotionForm'

export default function MessagesPage() {
  const account = useQuery({ queryKey: ['messages', 'account'], queryFn: smsAccount, staleTime: 60_000 })

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">SMS messages</h1>
        {account.data && (
          <p className="text-sm text-muted">
            {account.data.live
              ? `SMS account balance: ${account.data.balance ?? 'unknown'}`
              : 'No SMS account set up - messages are only logged, not sent.'}
          </p>
        )}
      </div>
      <PromotionForm quietHours={account.data?.quietHours} />
      <div className="grid gap-5 lg:grid-cols-2">
        <CampaignResults />
        <Replies />
      </div>
      <MessageLog />
    </div>
  )
}
