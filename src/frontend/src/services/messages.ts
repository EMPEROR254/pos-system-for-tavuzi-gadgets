// SMS log, promotions, delivery results and customers' replies (/api/messages).
import { request } from './api'
import { toQuery } from './query'
import type { Page } from './users'

export type SmsMessage = {
  id: number
  phone: string
  body: string
  kind: 'RECEIPT' | 'PROMO' | 'TEST'
  status: 'QUEUED' | 'SENT' | 'FAILED'
  attempts: number
  error: string | null
  createdAt: string
  sentAt: string | null
  /** Held until this time (scheduled promotion or quiet hours). */
  sendAfter: string | null
  /** The network's delivery report: Success, Failed, Rejected, AbsentSubscriber... */
  deliveryStatus: string | null
  failureReason: string | null
}

/** Delivery reports that mean the SMS never reached the phone (same list as the server). */
const UNDELIVERED = ['Failed', 'Rejected', 'Expired', 'AbsentSubscriber']

export const canResend = (m: SmsMessage) =>
  m.status === 'FAILED' || (m.status === 'SENT' && UNDELIVERED.includes(m.deliveryStatus ?? ''))

export type CampaignInput = { message: string; boughtWithinDays?: number; category?: string; sendAt?: string }

export type CampaignResult = { campaignId: number; recipients: number; smsParts: number; sendsAt: string }

export type Campaign = {
  id: number
  message: string
  audience: string
  createdAt: string
  sendAt: string | null
  recipients: number
  sent: number
  delivered: number
  failed: number
  waiting: number
}

export type SmsReply = { id: number; phone: string; text: string; optedOut: boolean; receivedAt: string }

export type SmsAccount = { live: boolean; balance: string | null; quietHours: string }

export const listMessages = (f: { kind?: string; status?: string; phone?: string; page?: number; size?: number }) =>
  request<Page<SmsMessage>>(`/api/messages${toQuery(f)}`)
export const previewCampaign = (body: CampaignInput) =>
  request<{ recipients: number; fullMessage: string; smsParts: number }>('/api/messages/campaigns/preview', {
    method: 'POST',
    body,
  })
export const sendCampaign = (body: CampaignInput) =>
  request<CampaignResult>('/api/messages/campaigns', { method: 'POST', body })
export const listCampaigns = () => request<Campaign[]>('/api/messages/campaigns')
export const listReplies = (page = 0) => request<Page<SmsReply>>(`/api/messages/replies${toQuery({ page, size: 10 })}`)
export const smsAccount = () => request<SmsAccount>('/api/messages/account')
export const resendMessage = (id: number) => request<void>(`/api/messages/${id}/resend`, { method: 'POST' })
