import {useApiKeyStore} from '../store/apiKeyStore'
import {getAuthHeaders, getApiBaseUrl} from './apiClient'
import {useChatStore} from '../store/chatStore'

const API_BASE = getApiBaseUrl()

interface ChatHandlers {
    onReasoning?: (reasoningContent: string) => void
    onToolCalls?: (toolCalls: any[]) => void
    onToolMessage?: (toolCallId: string, content: string) => void
    onContent?: (content: string) => void
    onSessionInit: (sessionId: number) => void
    onDone: () => void
    onError: (msg: string) => void
}

export function chatCompletion(
    userMessage: string,
    handlers: ChatHandlers,
    signal?: AbortSignal
): Promise<void> {
    const rawKey = useApiKeyStore.getState().apiKey
    const apiKey = typeof rawKey === "string" ? rawKey.trim() : ""
    if (!apiKey) {
        handlers.onError('请先设置 API Key')
        return Promise.resolve()
    }

    const store = useChatStore.getState()
    const effectiveSessionId = store.getEffectiveSessionId()

    // 构建引用会话ID列表
    const referencedSessionIds: number[] = store.referencedSessions.map((rs) => rs.id)

    const requestBody: Record<string, unknown> = {
        messages: [
            {role: "user", content: userMessage},
        ],
        max_tokens: store.maxTokens,
        thinking: {type: store.thinkingType},
        reasoning_effort: store.reasoningEffort,
        model: store.modelType === 'flash' ? 'deepseek-v4-flash' : 'deepseek-v4-pro',
        agent: store.currentAgentName,
        pattern: store.pattern,
        hook: store.hook,
        itemPath: store.itemPath || undefined,
        clientId: store.currentClientId || undefined,
    }

    if (effectiveSessionId !== undefined) {
        requestBody.sessionId = effectiveSessionId
    }

    // 携带引用会话ID列表
    if (referencedSessionIds.length > 0) {
        requestBody.referencedSessionIds = referencedSessionIds
    }

    const body = JSON.stringify(requestBody)

    return fetch(`${API_BASE}/chat/submit`, {
        method: "POST",
        headers: {
            ...getAuthHeaders(),
            "Content-Type": "application/json",
        },
        body,
        signal,
    })
        .then(async (response) => {
            if (!response.ok) {
                let errMsg = `请求失败 (${response.status})`
                try {
                    const errBody = await response.json().catch(() => null)
                    if (errBody?.message) errMsg = errBody.message
                } catch {
                }
                if (response.status === 401) {
                    errMsg = 'API Key无效或未授权，请检查你的 API Key'
                }
                if (response.status === 429) {
                    errMsg = '请求过于频繁，请稍后重试 (限流)'
                }
                throw new Error(errMsg)
            }

            const body = await response.json()
            const sessionId = body?.data
            if (!sessionId) {
                throw new Error('提交失败：未获取到会话ID')
            }

            handlers.onSessionInit(Number(sessionId))

            return
        })
        .catch((err) => {
            if (err.name === 'AbortError') {
                handlers.onDone()
                return
            }
            handlers.onError(err.message || '网络异常，请检查连接')
        })
}
