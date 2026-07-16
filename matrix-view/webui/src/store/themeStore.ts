import {create} from 'zustand'
import {persist} from 'zustand/middleware'
import type {ThemeMode} from '../types'

interface ThemeState {
    theme: ThemeMode
    toggle: () => void
    setTheme: (t: ThemeMode) => void
}

export const useThemeStore = create<ThemeState>()(
    persist(
        (set) => ({
            theme: 'light',
            toggle: () =>
                set((s) => {
                    const next = s.theme === 'light' ? 'dark' : 'light'
                    document.documentElement.classList.toggle('dark', next === 'dark')
                    return {theme: next}
                }),
            setTheme: (t) => {
                document.documentElement.classList.toggle('dark', t === 'dark')
                set({theme: t})
            },
        }),
        {name: 'matrix-theme'}
    )
)
