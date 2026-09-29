// SMS log and promotions (/api/messages).
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
}

export type CampaignInput = { message: string; boughtWithinDays?: number; category?: string }

export const listMessages = (f: { kind?: string; status?: string; page?: number; size?: number }) =>
  request<Page<SmsMessage>>(`/api/messages${toQuery(f)}`)
export const previewCampaign = (body: CampaignInput) =>
  request<{ recipients: number; fullMessage: string; smsParts: number }>('/api/messages/campaigns/preview', {
    method: 'POST',
    body,
  })
export const sendCampaign = (body: CampaignInput) =>
  request<{ campaignId: number; recipients: number; smsParts: number }>('/api/messages/campaigns', {
    method: 'POST',
    body,
  })
