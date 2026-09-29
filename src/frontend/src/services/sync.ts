// What is still waiting on the server to go out once the internet is back (/api/sync/status).
import { request } from './api'

export type ServerWaiting = {
  serverTime: string
  etimsMode: string
  etimsPending: number
  etimsRejected: number
  smsQueued: number
}

export const serverWaiting = () => request<ServerWaiting>('/api/sync/status')
