// Small success/error messages in the corner. Call toast.success('Saved') from
// anywhere; <Toaster /> in the app frame shows them.
import { create } from 'zustand'

export type Toast = { id: number; kind: 'success' | 'error'; message: string }

type ToastState = {
  toasts: Toast[]
  dismiss: (id: number) => void
  push: (kind: Toast['kind'], message: string) => void
}

let nextId = 1

export const useToasts = create<ToastState>((set) => ({
  toasts: [],
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
  push: (kind, message) => {
    const id = nextId++
    set((s) => ({ toasts: [...s.toasts, { id, kind, message }] }))
    setTimeout(() => useToasts.getState().dismiss(id), 4000)
  },
}))

export const toast = {
  success: (message: string) => useToasts.getState().push('success', message),
  error: (message: string) => useToasts.getState().push('error', message),
}
