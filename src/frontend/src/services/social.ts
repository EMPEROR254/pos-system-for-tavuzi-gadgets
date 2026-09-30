// Social media (/api/social): linked accounts and posts with a picture.
import { request } from './api'
import { toQuery } from './query'
import type { Page } from './users'

export type SocialNetwork = 'FACEBOOK' | 'INSTAGRAM' | 'TIKTOK' | 'WHATSAPP'

export const NETWORK_LABELS: Record<SocialNetwork, string> = {
  FACEBOOK: 'Facebook',
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
  WHATSAPP: 'WhatsApp',
}

export type SocialAccount = {
  network: SocialNetwork
  linked: boolean
  name: string | null
  linkedAt: string | null
  /** False until the server is set up for this network; "setup" says what is missing. */
  canLink: boolean
  setup: string | null
}

export type TargetStatus = 'PENDING' | 'POSTED' | 'SENT_TO_INBOX' | 'FAILED'

export type SocialPost = {
  id: number
  title: string
  description: string | null
  caption: string
  imageUrl: string
  createdByName: string | null
  createdAt: string
  targets: { network: SocialNetwork; status: TargetStatus; error: string | null; attempts: number }[]
}

export type NewPost = {
  title: string
  description: string
  networks: SocialNetwork[]
  whatsAppLink: boolean
  image: File
}

export const listAccounts = () => request<SocialAccount[]>('/api/social/accounts')

/** Facebook also links the Instagram business account connected to the Page. */
export const startLink = (network: SocialNetwork) =>
  request<{ authorizeUrl: string }>(`/api/social/accounts/${network}/link`, { method: 'POST' })

export const unlinkAccount = (network: SocialNetwork) =>
  request<void>(`/api/social/accounts/${network}`, { method: 'DELETE' })

export const setWhatsAppNumber = (number: string) =>
  request<SocialAccount>('/api/social/accounts/whatsapp', { method: 'PUT', body: { number } })

export function createPost(post: NewPost) {
  const form = new FormData()
  form.append('title', post.title)
  form.append('description', post.description)
  post.networks.forEach((n) => form.append('networks', n))
  form.append('whatsAppLink', String(post.whatsAppLink))
  form.append('image', post.image)
  return request<SocialPost>('/api/social/posts', { method: 'POST', form })
}

export const listPosts = (page = 0) => request<Page<SocialPost>>(`/api/social/posts${toQuery({ page, size: 10 })}`)

export const retryPost = (id: number, network: SocialNetwork) =>
  request<void>(`/api/social/posts/${id}/retry/${network}`, { method: 'POST' })
