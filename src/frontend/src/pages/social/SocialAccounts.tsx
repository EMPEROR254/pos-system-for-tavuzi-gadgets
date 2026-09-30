// The shop's linked social media. The owner links Facebook (with the Instagram
// business account connected to the Page) and TikTok through their own sign-in
// pages, and saves the WhatsApp Business number.
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from '../../components/Button'
import { inputClass } from '../../components/Field'
import { ApiError } from '../../services/api'
import { NETWORK_LABELS, setWhatsAppNumber, startLink, unlinkAccount, type SocialAccount } from '../../services/social'
import { formatDate } from '../../utils/format'
import { goToExternal } from '../../utils/navigate'
import { toast } from '../../utils/toast'

const HELP: Record<SocialAccount['network'], string> = {
  FACEBOOK: "The shop's Facebook Page.",
  INSTAGRAM: 'A business account connected to the Facebook Page - linked with Facebook.',
  TIKTOK: 'Posts go to your TikTok inbox; add a sound and publish in the TikTok app.',
  WHATSAPP: 'Shared from your phone to your Status or chats; the number adds an "Order on WhatsApp" link.',
}

export function SocialAccounts({ accounts, canManage }: { accounts: SocialAccount[]; canManage: boolean }) {
  return (
    <section className="space-y-3">
      <h2 className="font-semibold">Accounts</h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {accounts.map((a) => (
          <AccountCard key={a.network} account={a} canManage={canManage} />
        ))}
      </ul>
    </section>
  )
}

function AccountCard({ account: a, canManage }: { account: SocialAccount; canManage: boolean }) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [number, setNumber] = useState('')
  const label = NETWORK_LABELS[a.network]

  const run = async (action: () => Promise<unknown>, done?: string) => {
    setBusy(true)
    try {
      await action()
      if (done) toast.success(done)
      await queryClient.invalidateQueries({ queryKey: ['social', 'accounts'] })
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : 'Could not do that.')
    } finally {
      setBusy(false)
    }
  }

  // Off to Facebook/TikTok to agree; they send the owner back to this screen.
  const link = () => run(async () => goToExternal((await startLink(a.network)).authorizeUrl))

  return (
    <li className="space-y-3 rounded-2xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold">{label}</p>
          <p className="text-sm text-muted">{HELP[a.network]}</p>
        </div>
        <span
          className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${
            a.linked ? 'bg-brand-soft' : 'bg-background text-muted'
          }`}
        >
          {a.linked ? 'Linked' : 'Not linked'}
        </span>
      </div>
      {a.linked && (
        <p className="text-sm">
          <span className="font-medium">{a.name}</span>
          {a.linkedAt && <span className="text-muted"> · since {formatDate(a.linkedAt)}</span>}
        </p>
      )}
      {canManage && a.network === 'WHATSAPP' && (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            void run(() => setWhatsAppNumber(number), 'WhatsApp number saved.').then(() => setNumber(''))
          }}
        >
          <input
            aria-label="WhatsApp Business number"
            inputMode="tel"
            placeholder={a.linked ? 'Change number' : '07XX XXX XXX'}
            value={number}
            onChange={(e) => setNumber(e.target.value)}
            className={inputClass}
          />
          <Button type="submit" variant="secondary" busy={busy} disabled={!number.trim()}>
            Save
          </Button>
        </form>
      )}
      {canManage && a.network !== 'WHATSAPP' && (
        <div className="flex flex-wrap gap-2">
          {a.canLink ? (
            <Button variant={a.linked ? 'secondary' : 'primary'} busy={busy} onClick={() => void link()}>
              {a.linked ? 'Link again' : `Link ${a.network === 'INSTAGRAM' ? 'with Facebook' : label}`}
            </Button>
          ) : (
            <p className="text-sm text-muted">{a.setup}</p>
          )}
          {a.linked && (
            <Button
              variant="ghost"
              disabled={busy}
              onClick={() =>
                window.confirm(`Unlink ${label}? Posts will stop going there.`) &&
                void run(() => unlinkAccount(a.network), `${label} unlinked.`)
              }
            >
              Unlink
            </Button>
          )}
        </div>
      )}
    </li>
  )
}
