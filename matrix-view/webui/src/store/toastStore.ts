import {create} from 'zustand'
import type {Toast} from '../types'

function genId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

interface ToastState {
    toasts: Toast[]
    addToast: (t: Omit<Toast, 'id'>) => void
    removeToast: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
    toasts: [],
    addToast: (t) => {
        const id = genId()
        const toast: Toast = {...t, id}
        set((s) => ({toasts: [...s.toasts, toast]}))
        const duration = t.duration ?? 4000
        setTimeout(() => {
            set((s) => ({toasts: s.toasts.filter((x) => x.id !== id)}))
        }, duration)
    },
    removeToast: (id) =>
        set((s) => ({toasts: s.toasts.filter((x) => x.id !== id)})),
}))
