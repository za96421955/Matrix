import {useApiKeyStore} from '../store/apiKeyStore'

export function getApiBaseUrl(): string {
    return import.meta.env.VITE_API_BASE_URL || '/v1'
}

export function getAuthHeaders(): Record<string, string> {
    const apiKey = useApiKeyStore.getState().apiKey
    const deviceId = localStorage.getItem('__device_id__') || ''
    return {
        'Authorization': `Bearer ${apiKey}`,
        'X-Device-Id': deviceId,
    }
}

const BASE_URL = getApiBaseUrl()

async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${BASE_URL}${path}`, {
        ...options,
        headers: {
            ...getAuthHeaders(),
            'Content-Type': 'application/json',
            ...options?.headers,
        },
    })
    if (!res.ok) {
        let errMsg = `请求失败 (${res.status})`
        try {
            const errBody = await res.json().catch(() => null)
            if (errBody?.message) errMsg = errBody.message
        } catch {
            // ignore
        }
        throw new Error(errMsg)
    }
    return res.json()
}

export const api = {
    get: <T>(path: string) => request<T>(path),
    post: <T>(path: string, body?: unknown) =>
        request<T>(path, {method: 'POST', body: JSON.stringify(body)}),
    put: <T>(path: string, body?: unknown) =>
        request<T>(path, {method: 'PUT', body: JSON.stringify(body)}),
    delete: <T>(path: string) => request<T>(path, {method: 'DELETE'}),
}
