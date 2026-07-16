import {create} from 'zustand'
import {useApiKeyStore} from './apiKeyStore'
import {useToastStore} from './toastStore'
import {getAuthHeaders, getApiBaseUrl} from '../utils/apiClient'
import type {TaskInfo} from '../types'

const API_BASE = getApiBaseUrl()

interface TaskAuthState {
    taskList: TaskInfo[]
    loading: boolean
    modalOpen: boolean
    lastNotifiedTaskIds: string[]
    pollingTimer: ReturnType<typeof setInterval> | null

    fetchWaitingAuthList: () => Promise<void>
    submitAuth: (taskId: string, approved: boolean, reason?: string) => Promise<void>
    startPolling: () => void
    stopPolling: () => void
    setModalOpen: (v: boolean) => void
    closeModal: () => void
}

export const useTaskAuthStore = create<TaskAuthState>((set, get) => ({
    taskList: [],
    loading: false,
    modalOpen: false,
    lastNotifiedTaskIds: [],
    pollingTimer: null,

    fetchWaitingAuthList: async () => {
        const apiKey = useApiKeyStore.getState().apiKey
        if (!apiKey) return

        set({loading: true})
        try {
            const res = await fetch(`${API_BASE}/task/waitingAuthList`, {
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'application/json',
                },
            })
            if (!res.ok) {
                set({loading: false})
                return
            }
            const body = await res.json()
            // CommonResponse<List<TaskInfo>> -> body.data
            const rawList: unknown[] = body?.data ?? []
            const list: TaskInfo[] = Array.isArray(rawList)
                ? rawList.map((item: any) => ({
                    id: item.id,
                    taskId: item.taskId || '',
                    agentName: item.agentName || '',
                    type: item.type || '',
                    status: item.status || '',
                    content: item.content || '',
                    result: item.result || '',
                    createTime: item.createTime || '',
                }))
                : []

            set({taskList: list, loading: false})

            //空列表自动关闭弹窗
            if (list.length === 0) {
                set({modalOpen: false})
                return
            }

            //检查是否有新任务
            const state = get()
            if (!state.modalOpen) {
                const newTaskIds = list
                    .map((t) => t.taskId)
                    .filter((id) => !state.lastNotifiedTaskIds.includes(id))

                if (newTaskIds.length > 0) {
                    //用户正在输入框中打字时不弹窗，等下次轮询
                    const el = document.activeElement
                    const isTyping =
                        el?.tagName === 'TEXTAREA' ||
                        el?.id === 'chat-input' ||
                        el?.getAttribute('role') === 'textbox'
                    if (!isTyping) {
                        set({modalOpen: true})
                    }
                }
            }
        } catch {
            set({loading: false})
        }
    },

    submitAuth: async (taskId: string, approved: boolean, reason?: string) => {
        const apiKey = useApiKeyStore.getState().apiKey
        if (!apiKey) return

        try {
            const res = await fetch(`${API_BASE}/task/auth/${taskId}`, {
                method: 'POST',
                headers: {
                    ...getAuthHeaders(),
                    'Content-Type': 'text/plain',
                },
                body: approved ? '' : (reason ?? ''),
            })
            if (!res.ok) {
                useToastStore.getState().addToast({
                    type: 'error',
                    message: '授权操作失败 (' + res.status + ')',
                })
                return
            }

            //从列表移除该任务
            set((s) => ({
                taskList: s.taskList.filter((t) => t.taskId !== taskId),
            }))

            useToastStore.getState().addToast({
                type: 'success',
                message: approved ? '已授权执行' : '已拒绝执行',
                duration: 3000,
            })

            //列表为空自动关弹窗
            if (get().taskList.length === 0) {
                set({modalOpen: false})
            }
        } catch {
            useToastStore.getState().addToast({
                type: 'error',
                message: '网络异常，授权操作失败',
            })
        }
    },

    startPolling: () => {
        const state = get()
        if (state.pollingTimer) return

        //立即拉取一次
        state.fetchWaitingAuthList()

        const timer = setInterval(() => {
            get().fetchWaitingAuthList()
        }, 5000)

        set({pollingTimer: timer})
    },

    stopPolling: () => {
        const state = get()
        if (state.pollingTimer) {
            clearInterval(state.pollingTimer)
            set({pollingTimer: null})
        }
    },

    setModalOpen: (v: boolean) => {
        set({modalOpen: v})
    },

    closeModal: () => {
        const state = get()
        const currentIds = state.taskList.map((t) => t.taskId)
        set((s) => ({
            modalOpen: false,
            lastNotifiedTaskIds: [...new Set([...s.lastNotifiedTaskIds, ...currentIds])],
        }))
    },
}))
