import {create} from 'zustand'
import {persist, createJSONStorage} from 'zustand/middleware'

interface ApiKeyState {
    apiKey: string
    setApiKey: (key: string) => void
    clearApiKey: () => void
}

//清除可能存在的旧版本错误数据
function sanitizeApiKey(value: unknown): string {
    if (typeof value === 'string') return value.trim()
    //如果是对象（可能是 persist读取到的错误数据），返回空字符串以刷新
    return ''
}

export const useApiKeyStore = create<ApiKeyState>()(
    persist(
        (set) => ({
            apiKey: '',
            setApiKey: (key: string) => set({apiKey: key.trim()}),
            clearApiKey: () => set({apiKey: ''}),
        }),
        {
            name: 'matrix-apikey',
            storage: createJSONStorage(() => localStorage),
            merge: (persisted, current) => ({
                ...current,
                apiKey: sanitizeApiKey((persisted as any)?.apiKey),
            }),
        }
    )
)
