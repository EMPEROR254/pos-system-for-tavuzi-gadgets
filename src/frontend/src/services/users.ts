// Staff accounts: /api/users (owner only).
import type { Role } from '../utils/permissions'
import { request } from './api'

export type User = {
  id: number
  username: string
  fullName: string
  role: Role
  active: boolean
  locked: boolean
  createdAt: string
}

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type UserFilters = {
  q?: string
  role?: Role | ''
  active?: 'true' | 'false' | ''
  page?: number
  size?: number
  sort?: 'fullName' | 'username' | 'role' | 'createdAt'
  dir?: 'asc' | 'desc'
}

export function listUsers(filters: UserFilters): Promise<Page<User>> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return request<Page<User>>(`/api/users?${params}`)
}

export type NewUser = { username: string; fullName: string; role: Role; password: string }

export function createUser(user: NewUser): Promise<User> {
  return request<User>('/api/users', { method: 'POST', body: user })
}

export type UserChanges = { fullName: string; role: Role; active: boolean }

export function updateUser(id: number, changes: UserChanges): Promise<User> {
  return request<User>(`/api/users/${id}`, { method: 'PUT', body: changes })
}

export function resetPassword(id: number, password: string): Promise<void> {
  return request<void>(`/api/users/${id}/password`, { method: 'POST', body: { password } })
}
