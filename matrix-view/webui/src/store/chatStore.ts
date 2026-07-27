import {create} from 'zustand'
import {persist} from 'zustand/middleware'
import type {Message, Session, BackendSessionSummary, ToolCall, Pattern} from '../types'
import {useApiKeyStore} from './apiKeyStore'
import {useToastStore} from './toastStore'
import {getAuthHeaders, getApiBaseUrl} from '../utils/apiClient'

const API_BASE = getApiBaseUrl()

function genId() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

export function isBackendSessionId(id: string | null): boolean {
    return id !== null && /^\d+$/.test(id)
}

/**
 * 从文本中精确提取有效的 @会话引用段（@标题 格式，以空格结尾）
 * 使用完整精确匹配，支持标题含 @、换行、括号等特殊字符
 * 标题中的换行/制表符在匹配前会被标准化为空格，以兼容前端输入框的规范化处理
 * @param text 输入文本
 * @param sessions 可用会话列表
 * @returns 匹配成功的引用段数组，如 ['@标题A ', '@标题B ']
 */
export function extractValidReferenceStrings(text: string, sessions: BackendSessionSummary[]): string[] {
    const result: string[] = [];
    // 按标题长度降序排序，优先匹配长标题（避免短标题误匹配长标题的一部分）
    const sorted = [...sessions].sort((a, b) => b.title.length - a.title.length);
    const seen = new Set<string>();
    for (const session of sorted) {
        // 标准化标题：将换行/制表符替换为空格，与前端输入框插入时的处理一致
        const normalizedTitle = session.title.replace(/[\r\n\t]+/g, ' ').trim();
        const refStr = '@' + normalizedTitle + ' ';
        if (!seen.has(refStr) && text.includes(refStr)) {
            result.push(refStr);
            seen.add(refStr);
        }
        // 同时尝试匹配原始标题（兼容历史消息中的非标准化引用）
        if (normalizedTitle !== session.title) {
            const originalRefStr = '@' + session.title + ' ';
            if (!seen.has(originalRefStr) && text.includes(originalRefStr)) {
                result.push(originalRefStr);
                seen.add(originalRefStr);
            }
        }
    }
    return result;
}

function mergeToolCallsDelta(existing: ToolCall[] | undefined, delta: any[]): ToolCall[] {
    const merged = existing ? [...existing] : [];
    for (const tc of delta) {
        if (tc.index === undefined || tc.index === null) continue;
        const idx = Number(tc.index);

        if (merged[idx]) {
            const old = merged[idx];
            let newArgs = old.function.arguments || '';
            const incomingArgs = tc.function?.arguments || '';
            if (incomingArgs) {
                //智能合并：全量替换 vs增量追加
                if (incomingArgs.startsWith(newArgs)) {
                    newArgs = incomingArgs;
                } else {
                    newArgs += incomingArgs;
                }
            }
            //不可变更新：创建新对象
            merged[idx] = {
                ...old,
                id: tc.id || old.id,
                type: tc.type || old.type,
                function: {
                    name: tc.function?.name || old.function.name,
                    arguments: newArgs,
                },
            };
        } else {
            merged[idx] = {
                id: tc.id || '',
                index: idx,
                type: tc.type || 'function',
                function: {
                    name: tc.function?.name || '',
                    arguments: tc.function?.arguments || '',
                },
            };
        }
    }
    return merged.filter(Boolean);
}

interface ChatState {
    sessions: Session[]
    currentSessionId: string | null
    isStreaming: boolean
    isBackendGenerating: boolean
    sidebarOpen: boolean
    historyMessages: Message[]
    historyPage: number
    historyTotal: number
    historyPages: number
    historyLoading: boolean
    historyLoaded: boolean
    backendSessionList: BackendSessionSummary[]
    sessionsPage: number
    sessionsTotal: number
    sessionsTotalPages: number
    sessionsLoading: boolean
    sessionsLoaded: boolean
    createSession: () => string
    selectSession: (id: string) => void
    deleteSession: (id: string) => void
    addMessage: (msg: Omit<Message, 'id' | 'timestamp'>) => void
    updateLastAssistantMessage: (content: string) => void
    updateLastAssistantReasoning: (deltaReasoning: string) => void
    updateLastAssistantToolCalls: (toolCalls: any[]) => void
    addToolMessage: (toolCallId: string, content: string) => void
    appendStreamMessage: (msg: Message) => void
    setStreaming: (v: boolean) => void
    setBackendGenerating: (v: boolean) => void
    setBackendSessionId: (sessionId: number) => void
    toggleSidebar: () => void
    setSidebarOpen: (v: boolean) => void
    getCurrentSession: () => Session | undefined
    getMessagesForApi: () => { role: string; content: string }[]
    currentAgentName: string
    agentList: string[]
    agentListLoading: boolean
    fetchAgentList: () => Promise<void>
    setCurrentAgent: (name: string) => void
    updateBackendSessionTitle: (sessionId: number, title: string) => Promise<void>
    updateAuthLevel: (sessionId: number, authLevel: number) => Promise<void>;
    updateAgent: (sessionId: number, agent: string) => Promise<void>;
    deleteBackendSession: (sessionId: number) => Promise<void>
    fetchSessions: (page?: number) => Promise<void>
    loadMoreSessions: () => Promise<void>
    selectBackendSession: (id: number) => void
    clearCurrentSession: () => void
    getEffectiveSessionId: () => number | undefined
    /** 引用的会话列表 */
    referencedSessions: BackendSessionSummary[]
    addReferencedSession: (session: BackendSessionSummary) => void
    removeReferencedSession: (sessionId: number) => void
    clearReferencedSessions: () => void
    addHistoryMessage: (msg: Omit<Message, 'id' | 'timestamp'>) => void
    updateLastHistoryMessage: (content: string) => void
    updateLastHistoryReasoning: (deltaReasoning: string) => void
    updateLastHistoryToolCalls: (toolCalls: any[]) => void
    addHistoryToolMessage: (toolCallId: string, content: string) => void
    appendHistoryStreamMessage: (msg: Message) => void
    fetchHistoryMessages: (sessionId: number, page?: number) => Promise<void>
    loadMoreHistoryMessages: () => Promise<void>
    clearHistoryMessages: () => void
    deleteHistoryMessage: (sessionId: number, msgId: number) => Promise<void>
    removeLastAssistantMessage: () => void
    removeLastHistoryMessage: () => void
    thinkingType: "enabled" | "disabled"
    reasoningEffort: "high" | "max"
    modelType: 'flash' | 'pro'
    markdownEnabled: boolean
    setThinkingType: (v: "enabled" | "disabled") => void
    setReasoningEffort: (v: "high" | "max") => void
    setModelType: (v: 'flash' | 'pro') => void
    setMarkdownEnabled: (v: boolean) => void
    messageFilterMode: 'all' | 'chat'
    setMessageFilterMode: (mode: 'all' | 'chat') => void
    maxTokens: number
    setMaxTokens: (v: number) => void
    userAuthLevel: number
    setUserAuthLevel: (level: number) => void
    fetchUserAuthLevel: () => Promise<void>
    pattern: Pattern
    itemPath: string
    setItemPath: (v: string) => void
    projectPathHistory: string[]
    addProjectPath: (path: string) => void
    removeProjectPath: (path: string) => void
    currentClientId: string
    setCurrentClientId: (v: string) => void
    setPattern: (v: Pattern) => void
}

const PAGE_SIZE = 20

export const useChatStore = create<ChatState>()(
    persist(
        (set, get) => ({
            sessions: [],
            currentSessionId: null,
            isStreaming: false,
            isBackendGenerating: false,
            sidebarOpen: true,
            currentAgentName: 'plan_agent',
            thinkingType: 'enabled',
            reasoningEffort: 'high',
            modelType: 'flash',
            markdownEnabled: true,
            messageFilterMode: 'all',
            maxTokens: 8192,
            agentList: [],
            agentListLoading: false,
            historyMessages: [],
            historyPage: 1,
            historyTotal: 0,
            historyPages: 1,
            historyLoading: false,
            historyLoaded: false,
            backendSessionList: [],
            sessionsPage: 1,
            sessionsTotal: 0,
            sessionsTotalPages: 1,
            sessionsLoading: false,
            sessionsLoaded: false,
            userAuthLevel: 0,
            referencedSessions: [],
            pattern: 'auto',
            itemPath: '',
            projectPathHistory: [],
            currentClientId: '',
            createSession: () => {
                const id = genId()
                const now = Date.now()
                const session: Session = {
                    id,
                    title: '新对话',
                    messages: [],
                    createdAt: now,
                    updatedAt: now,
                }
                set((s) => ({
                    sessions: [session, ...s.sessions],
                    currentSessionId: id,
                }))
                return id
            },

            selectSession: (id) => {
                set({currentSessionId: id})
            },

            deleteSession: (id) => {
                set((s) => ({
                    sessions: s.sessions.filter((ss) => ss.id !== id),
                    currentSessionId:
                        s.currentSessionId === id
                            ? s.sessions.length > 1
                                ? s.sessions.find((ss) => ss.id !== id)?.id ?? null
                                : null
                            : s.currentSessionId,
                }))
            },

            addMessage: (msg) => {
                const id = genId()
                const full: Message = {...msg, id, timestamp: Date.now()}
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId
                            ? {
                                ...ss,
                                messages: [...ss.messages, full],
                                updatedAt: Date.now(),
                                title:
                                    ss.messages.length === 0 && msg.role === 'user'
                                        ? msg.content.slice(0, 30)
                                        : ss.title,
                            }
                            : ss
                    ),
                }))
            },

            appendStreamMessage: (msg) => {
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId
                            ? {...ss, messages: [...ss.messages, msg], updatedAt: Date.now()}
                            : ss
                    ),
                }))
            },

            // ===NEW: updateLastAssistantReasoning===
            updateLastAssistantReasoning: (deltaReasoning) => {
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId && ss.messages.length > 0
                            ? {
                                ...ss,
                                messages: (() => {
                                    const msgs = [...ss.messages];
                                    let last = msgs[msgs.length - 1];
                                    if (last.role !== 'assistant') {
                                        last = {
                                            id: genId(),
                                            role: 'assistant',
                                            content: '',
                                            reasoningContent: '',
                                            timestamp: Date.now()
                                        };
                                        msgs.push(last);
                                    }
                                    last.reasoningContent = (last.reasoningContent || '') + deltaReasoning;
                                    msgs[msgs.length - 1] = last;
                                    return msgs;
                                })(),
                            }
                            : ss
                    ),
                }))
            },

            // ===NEW: updateLastAssistantToolCalls===
            updateLastAssistantToolCalls: (toolCalls) => {
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId && ss.messages.length > 0
                            ? {
                                ...ss,
                                messages: (() => {
                                    const msgs = [...ss.messages];
                                    let last = msgs[msgs.length - 1];
                                    if (last.role !== 'assistant') {
                                        last = {id: genId(), role: 'assistant', content: '', timestamp: Date.now()};
                                        msgs.push(last);
                                    }
                                    last.toolCalls = mergeToolCallsDelta(last.toolCalls, toolCalls);
                                    msgs[msgs.length - 1] = last;
                                    return msgs;
                                })(),
                            }
                            : ss
                    ),
                }))
            },

            // ===NEW: addToolMessage===
            addToolMessage: (toolCallId, content) => {
                var msg = {id: genId(), role: 'tool' as const, content, toolCallId, timestamp: Date.now()};
                get().appendStreamMessage(msg);
            },

            // ===MODIFIED: updateLastAssistantMessage with auto-create===
            updateLastAssistantMessage: (deltaContent) => {
                set((s) => {
                    const sessIdx = s.sessions.findIndex(ss => ss.id === s.currentSessionId);
                    if (sessIdx === -1) return s;
                    const session = s.sessions[sessIdx];
                    const msgs = session.messages;
                    const lastIdx = msgs.length - 1;
                    if (lastIdx < 0) return s;
                    let last = msgs[lastIdx];
                    if (last.role !== 'assistant') {
                        last = {id: genId(), role: 'assistant', content: '', timestamp: Date.now()};
                        const newSessions = [...s.sessions];
                        newSessions[sessIdx] = {...session, messages: [...msgs, last], updatedAt: Date.now()};
                        return {...s, sessions: newSessions};
                    }
                    const newLast = {...last, content: last.content + deltaContent};
                    const newMsgs = [...msgs];
                    newMsgs[lastIdx] = newLast;
                    const newSessions = [...s.sessions];
                    newSessions[sessIdx] = {...session, messages: newMsgs, updatedAt: Date.now()};
                    return {...s, sessions: newSessions};
                })
            },

            setStreaming: (v) => set({isStreaming: v}),
            setBackendGenerating: (v) => set({isBackendGenerating: v}),
            setBackendSessionId: (sessionId) => {
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId
                            ? {...ss, backendSessionId: sessionId}
                            : ss
                    ),
                }))
            },

            toggleSidebar: () => set((s) => ({sidebarOpen: !s.sidebarOpen})),
            setSidebarOpen: (v) => set({sidebarOpen: v}),

            getCurrentSession: () => {
                const {sessions, currentSessionId} = get()
                return sessions.find((ss) => ss.id === currentSessionId)
            },

            getMessagesForApi: () => {
                const session = get().getCurrentSession()
                if (!session) return []
                return session.messages.map((m) => ({
                    role: m.role,
                    content: m.content,
                }))
            },

            // ===== Agent =====

            fetchAgentList: async () => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                set({agentListLoading: true})
                try {
                    const res = await fetch(`${API_BASE}/agent/list`, {
                        headers: {...getAuthHeaders(), 'Content-Type': 'application/json'},
                    })
                    if (!res.ok) {
                        set({agentListLoading: false});
                        return
                    }
                    const body = await res.json()
                    const list = Array.isArray(body?.data) ? body.data : []
                    set({agentList: list, agentListLoading: false})
                } catch {
                    set({agentListLoading: false})
                }
            },

            setCurrentAgent: (name: string) => {
                set({currentAgentName: name})
            },

            // ===== Backend session list =====

            fetchSessions: async (page?: number) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return

                const targetPage = page ?? 1
                set({sessionsLoading: true})

                try {
                    const res = await fetch(`${API_BASE}/session/page/${targetPage}/${PAGE_SIZE}`, {
                        headers: {
                            ...getAuthHeaders(),
                            'Content-Type': 'application/json',
                        },
                    })

                    if (!res.ok) {
                        set({sessionsLoading: false})
                        return
                    }

                    const body = await res.json()
                    const pageData = body?.data
                    const rawList: unknown[] = pageData?.records ?? []
                    const list: BackendSessionSummary[] = Array.isArray(rawList)
                        ? rawList.map((item: any) => ({
                            id: item.id,
                            title: item.title || '',
                            updateTime: item.updateTime || '',
                            authLevel: item.authLevel != null ? item.authLevel : undefined,
                            agent: item.agent != null ? item.agent : undefined,
                        }))
                        : []
                    const total: number = typeof pageData?.total === 'number' ? pageData.total : 0
                    const current: number = typeof pageData?.current === 'number' ? pageData.current : targetPage
                    const pages: number = typeof pageData?.pages === 'number' ? pageData.pages : 1

                    set((s) => ({
                        backendSessionList: targetPage === 1 ? list : [...s.backendSessionList, ...list],
                        sessionsPage: current,
                        sessionsTotal: total,
                        sessionsTotalPages: pages,
                        sessionsLoading: false,
                        sessionsLoaded: true,
                    }))
                } catch {
                    set({sessionsLoading: false})
                }
            },

            loadMoreSessions: async () => {
                const state = get()
                if (state.sessionsLoading) return
                if (state.sessionsPage >= state.sessionsTotalPages) return
                const nextPage = state.sessionsPage + 1
                if (nextPage > state.sessionsTotalPages) return
                await state.fetchSessions(nextPage)
            },

            selectBackendSession: (id: number) => {
                set({
                    currentSessionId: String(id),
                    historyMessages: [],
                    historyPage: 1,
                    historyLoaded: false,
                })
                if (window.innerWidth < 768) {
                    set({sidebarOpen: false})
                }
                get().fetchHistoryMessages(id, 1)
                // Check backend generating status on (re-)select
                const apiKey = useApiKeyStore.getState().apiKey
                if (apiKey) {
                    fetch(`${API_BASE}/session/isCompletions/${id}`, {
                        headers: {...getAuthHeaders()},
                    })
                        .then(r => r.json())
                        .then(body => {
                            set({isBackendGenerating: body?.data === true})
                        })
                        .catch(() => {
                        })
                }
            },

            clearCurrentSession: () => {
                set({currentSessionId: null, historyMessages: [], historyLoaded: false})
                if (window.innerWidth < 768) {
                    set({sidebarOpen: false})
                }
            },

            getEffectiveSessionId: () => {
                const state = get()
                const sid = state.currentSessionId
                if (!sid) return undefined
                if (isBackendSessionId(sid)) return Number(sid)
                const session = state.sessions.find((s) => s.id === sid)
                return session?.backendSessionId
            },

            // ===== Session CRUD =====

            updateBackendSessionTitle: async (sessionId: number, title: string) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/session/updateTitle/${sessionId}`, {
                        method: 'PUT',
                        headers: {...getAuthHeaders(), 'Content-Type': 'application/json'},
                        body: title,
                    })
                    if (!res.ok) {
                        useToastStore.getState().addToast({type: 'error', message: '重命名失败 (' + res.status + ')'})
                        return
                    }
                    set((s) => ({
                        backendSessionList: s.backendSessionList.map((item) =>
                            item.id === sessionId ? {...item, title} : item
                        ),
                    }))
                } catch {
                    useToastStore.getState().addToast({type: 'error', message: '网络异常，重命名失败'})
                }
            },

            updateAuthLevel: async (sessionId: number, authLevel: number) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/session/updateAuthLevel/${sessionId}`, {
                        method: "PUT",
                        headers: {...getAuthHeaders(), "Content-Type": "application/json"},
                        body: authLevel + "",
                    })
                    if (!res.ok) {
                        useToastStore.getState().addToast({
                            type: "error",
                            message: "更新授权等级失败 (" + res.status + ")"
                        })
                        return
                    }
                    set((s) => ({
                        backendSessionList: s.backendSessionList.map((item) =>
                            item.id === sessionId ? {...item, authLevel} : item
                        ),
                    }))
                } catch {
                    useToastStore.getState().addToast({type: "error", message: "网络异常，更新授权等级失败"})
                }
            },
            updateAgent: async (sessionId: number, agent: string) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/session/updateAgent/${sessionId}`, {
                        method: "PUT",
                        headers: {...getAuthHeaders(), "Content-Type": "application/json"},
                        body: agent,
                    })
                    if (!res.ok) {
                        useToastStore.getState().addToast({
                            type: "error",
                            message: "更新技能失败 (" + res.status + ")"
                        })
                        return
                    }
                    set((s) => ({
                        backendSessionList: s.backendSessionList.map((item) =>
                            item.id === sessionId ? {...item, agent} : item
                        ),
                    }))
                } catch {
                    useToastStore.getState().addToast({type: "error", message: "网络异常，更新技能失败"})
                }
            },
            deleteBackendSession: async (sessionId: number) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/session/${sessionId}`, {
                        method: 'DELETE',
                        headers: {...getAuthHeaders(), 'Content-Type': 'application/json'},
                    })
                    if (!res.ok) {
                        useToastStore.getState().addToast({type: 'error', message: '删除失败 (' + res.status + ')'})
                        return
                    }
                    const state = get()
                    const wasActive = state.currentSessionId === String(sessionId)
                    set((s) => {
                        const newList = s.backendSessionList.filter((item) => item.id !== sessionId)
                        let nextId = s.currentSessionId
                        let nextHistory: Message[] = s.historyMessages
                        if (wasActive) {
                            const sorted = [...newList].sort(
                                (a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime()
                            )
                            if (sorted.length > 0) {
                                nextId = String(sorted[0].id)
                                nextHistory = []
                                setTimeout(() => get().fetchHistoryMessages(sorted[0].id, 1), 0)
                            } else {
                                nextId = null
                                nextHistory = []
                            }
                        }
                        return {
                            backendSessionList: newList,
                            currentSessionId: nextId,
                            historyMessages: nextHistory,
                        }
                    })
                } catch {
                    useToastStore.getState().addToast({type: 'error', message: '网络异常，删除失败'})
                }
            },

            addHistoryMessage: (msg) => {
                const id = genId()
                const full: Message = {...msg, id, timestamp: Date.now()}
                set((s) => ({historyMessages: [...s.historyMessages, full]}))
            },

            appendHistoryStreamMessage: (msg) => {
                set((s) => ({historyMessages: [...s.historyMessages, msg]}))
            },

            // ===NEW: updateLastHistoryReasoning===
            updateLastHistoryReasoning: (deltaReasoning) => {
                set((s) => {
                    const msgs = [...s.historyMessages];
                    if (msgs.length === 0) return s;
                    let last = msgs[msgs.length - 1];
                    if (last.role !== 'assistant') {
                        last = {
                            id: genId(),
                            role: 'assistant',
                            content: '',
                            reasoningContent: '',
                            timestamp: Date.now()
                        };
                        msgs.push(last);
                    }
                    last.reasoningContent = (last.reasoningContent || '') + deltaReasoning;
                    msgs[msgs.length - 1] = last;
                    return {historyMessages: msgs};
                })
            },

            // ===NEW: updateLastHistoryToolCalls===
            updateLastHistoryToolCalls: (toolCalls) => {
                set((s) => {
                    const msgs = [...s.historyMessages];
                    if (msgs.length === 0) return s;
                    let last = msgs[msgs.length - 1];
                    if (last.role !== 'assistant') {
                        last = {id: genId(), role: 'assistant', content: '', timestamp: Date.now()};
                        msgs.push(last);
                    }
                    last.toolCalls = mergeToolCallsDelta(last.toolCalls, toolCalls);
                    msgs[msgs.length - 1] = last;
                    return {historyMessages: msgs};
                })
            },

            // ===NEW: addHistoryToolMessage===
            addHistoryToolMessage: (toolCallId, content) => {
                var msg = {id: genId(), role: 'tool' as const, content, toolCallId, timestamp: Date.now()};
                get().appendHistoryStreamMessage(msg);
            },

            // ===MODIFIED: updateLastHistoryMessage with auto-create===
            updateLastHistoryMessage: (deltaContent) => {
                set((s) => {
                    const msgs = s.historyMessages;
                    const lastIdx = msgs.length - 1;
                    if (lastIdx < 0) return s;
                    let last = msgs[lastIdx];
                    if (last.role !== 'assistant') {
                        last = {id: genId(), role: 'assistant', content: '', timestamp: Date.now()};
                        return {historyMessages: [...msgs, last]};
                    }
                    const newLast = {...last, content: last.content + deltaContent};
                    const newMsgs = [...msgs];
                    newMsgs[lastIdx] = newLast;
                    return {historyMessages: newMsgs};
                })
            },

            removeLastAssistantMessage: () => {
                set((s) => ({
                    sessions: s.sessions.map((ss) =>
                        ss.id === s.currentSessionId && ss.messages.length > 0 && ss.messages[ss.messages.length - 1].role === "assistant"
                            ? {...ss, messages: ss.messages.slice(0, -1)}
                            : ss
                    ),
                }))
            },

            removeLastHistoryMessage: () => {
                set((s) => {
                    if (s.historyMessages.length === 0) return s
                    const last = s.historyMessages[s.historyMessages.length - 1]
                    if (last.role !== "assistant") return s
                    return {historyMessages: s.historyMessages.slice(0, -1)}
                })
            },

            setThinkingType: (v) => set({thinkingType: v}),
            setReasoningEffort: (v) => set({reasoningEffort: v}),
            setModelType: (v) => set({modelType: v}),
            setMarkdownEnabled: (v) => set({markdownEnabled: v}),
            setMessageFilterMode: (mode) => set({messageFilterMode: mode}),
            setMaxTokens: (v) => set({maxTokens: v}),
            setUserAuthLevel: (level: number) => set({userAuthLevel: level}),

            addReferencedSession: (session: BackendSessionSummary) => {
                set((s) => {
                    if (s.referencedSessions.find((rs) => rs.id === session.id)) return s;
                    return { referencedSessions: [...s.referencedSessions, session] };
                });
            },

            removeReferencedSession: (sessionId: number) => {
                set((s) => ({
                    referencedSessions: s.referencedSessions.filter((rs) => rs.id !== sessionId),
                }));
            },

            clearReferencedSessions: () => {
                set({ referencedSessions: [] });
            },
            setCurrentClientId: (v) => set({currentClientId: v}),
            setPattern: (v) => set({pattern: v}),
            setItemPath: (v) => set({itemPath: v}),
            addProjectPath: (path: string) => {
                const trimmed = path.trim()
                if (!trimmed) return
                set((s) => {
                    if (s.projectPathHistory.includes(trimmed)) return s
                    return { projectPathHistory: [trimmed, ...s.projectPathHistory].slice(0, 20) }
                })
            },
            removeProjectPath: (path: string) => {
                set((s) => ({
                    projectPathHistory: s.projectPathHistory.filter((p) => p !== path)
                }))
            },
            fetchUserAuthLevel: async () => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/user/getAuthLevel`, {
                        headers: {...getAuthHeaders(), "Content-Type": "application/json"},
                    })
                    if (!res.ok) return
                    const body = await res.json()
                    if (body?.data != null) {
                        set({userAuthLevel: Number(body.data)})
                    }
                } catch {
                }
            },

            fetchHistoryMessages: async (sessionId: number, page?: number) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                const targetPage = page ?? 1
                set({historyLoading: true})
                try {
                    const path = get().messageFilterMode === 'chat' ? `${API_BASE}/message/page/chat/` : `${API_BASE}/message/page/`
                    const res = await fetch(path + sessionId + '/' + targetPage + '/50', {
                        headers: {
                            ...getAuthHeaders(),
                            'Content-Type': 'application/json',
                        },
                    })
                    if (!res.ok) {
                        set({historyLoading: false})
                        return
                    }
                    const body = await res.json()
                    const pageData = body?.data
                    const rawList: unknown[] = pageData?.records ?? []
                    const list: Message[] = Array.isArray(rawList)
                        ? rawList
                            .map((item: any) => ({
                                id: String(item.id),
                                role: item.role || 'user',
                                content: item.content || '',
                                reasoningContent: item.reasoning_content || undefined,

                                toolCalls: (() => {
                                    try {
                                        const p = JSON.parse(item.tool_calls);
                                        return Array.isArray(p) ? p : undefined;
                                    } catch {
                                        return undefined;
                                    }
                                })(),
                                toolCallId: item.tool_call_id || undefined,
                                timestamp: item.createTime ? new Date(item.createTime).getTime() : Date.now(),
                            }))
                            .sort((a, b) => Number(a.id) - Number(b.id))
                        : []
                    const total: number = pageData?.total ?? 0
                    const pages: number = pageData?.pages ?? 1
                    set((s) => ({
                        historyMessages: targetPage === 1 ? list : [...list, ...s.historyMessages],
                        historyPage: targetPage,
                        historyTotal: total,
                        historyPages: pages,
                        historyLoading: false,
                        historyLoaded: true,
                    }))
                } catch {
                    set({historyLoading: false})
                }
            },

            loadMoreHistoryMessages: async () => {
                const state = get()
                if (state.historyLoading) return
                if (state.historyPage >= state.historyPages) return
                const sid = state.currentSessionId
                if (!sid || !isBackendSessionId(sid)) return
                const nextPage = state.historyPage + 1
                if (nextPage > state.historyPages) return
                await state.fetchHistoryMessages(Number(sid), nextPage)
            },

            clearHistoryMessages: () => {
                set({historyMessages: [], historyPage: 1, historyTotal: 0, historyPages: 1, historyLoaded: false})
            },

            deleteHistoryMessage: async (sessionId: number, msgId: number) => {
                const apiKey = useApiKeyStore.getState().apiKey
                if (!apiKey) return
                try {
                    const res = await fetch(`${API_BASE}/message/${sessionId}/${msgId}`, {
                        method: 'DELETE',
                        headers: {
                            ...getAuthHeaders(),
                            'Content-Type': 'application/json',
                        },
                    })
                    if (!res.ok) return
                    set((s) => ({
                        historyMessages: s.historyMessages.filter((m) => m.id !== String(msgId)),
                    }))
                } catch {
                }
            },
        }),
        {
            name: 'matrix-chat',
            partialize: (state) => ({
                currentSessionId: state.currentSessionId,
                thinkingType: state.thinkingType,
                reasoningEffort: state.reasoningEffort,
                modelType: state.modelType,
                markdownEnabled: state.markdownEnabled,
                messageFilterMode: state.messageFilterMode,
                maxTokens: state.maxTokens,
                pattern: state.pattern,
                itemPath: state.itemPath,
                projectPathHistory: state.projectPathHistory,
                currentClientId: state.currentClientId,
            }),
        }
    )
)