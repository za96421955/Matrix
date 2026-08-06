import React, {useEffect, useLayoutEffect, useRef, useCallback, useState, useMemo} from 'react'
import {Menu, Key, Loader2, ChevronUp, RefreshCw, FileText, MessageSquare, Folder, Monitor, ShieldCheck, ArrowLeftRight, Wrench, Zap, Route, Eye, Target, Brain} from 'lucide-react'
import {useChatStore, isBackendSessionId} from '../store/chatStore'
import {useApiKeyStore} from '../store/apiKeyStore'
import {useToastStore} from '../store/toastStore'
import {useTaskAuthStore} from '../store/taskAuthStore'
import MessageBubble from './MessageBubble'
import TypingIndicator from './TypingIndicator'
import InputBar from './InputBar'

const ApiKeyModal = React.lazy(() => import('./ApiKeyModal'))
const TaskAuthModal = React.lazy(() => import('./TaskAuthModal'))
import {chatCompletion} from '../utils/api'
import {Message, ToolCall, Pattern} from "@/types"
import {getAuthHeaders, getApiBaseUrl} from '../utils/apiClient';
import MatrixLogo from "./MatrixLogo";

const API_BASE = getApiBaseUrl()

// ===================== 模式 / 风险等级 视觉配置 =====================
const MODE_CONFIG: Record<Pattern, { label: string; icon: React.ComponentType<{ className?: string }>; dot: string; text: string; border: string; ring: string; chip: string; gradient: string; color: string; hover: string }> = {
    skill:  { label: '技能', icon: Wrench, dot: 'bg-green-500',   text: 'text-green-600 dark:text-green-300',    border: 'border-green-400/70 dark:border-green-500/50',    ring: 'focus:ring-green-500/50',   chip: 'bg-green-50 dark:bg-green-500/10', gradient: 'bg-gradient-to-r from-green-500/10 to-green-500/5', hover: 'hover:border-green-500/80 dark:hover:border-green-400/70', color: '#16a34a' },
    execute:{ label: '执行', icon: Zap,    dot: 'bg-cyan-500',    text: 'text-cyan-600 dark:text-cyan-300',       border: 'border-cyan-400/70 dark:border-cyan-500/50',       ring: 'focus:ring-cyan-500/50',  chip: 'bg-cyan-50 dark:bg-cyan-500/10', gradient: 'bg-gradient-to-r from-cyan-500/10 to-cyan-500/5', hover: 'hover:border-cyan-500/80 dark:hover:border-cyan-400/70', color: '#0891b2' },
    plan:   { label: '规划', icon: Route,  dot: 'bg-blue-500',    text: 'text-blue-600 dark:text-blue-300',       border: 'border-blue-400/70 dark:border-blue-500/50',       ring: 'focus:ring-blue-500/50',  chip: 'bg-blue-50 dark:bg-blue-500/10', gradient: 'bg-gradient-to-r from-blue-500/10 to-blue-500/5', hover: 'hover:border-blue-500/80 dark:hover:border-blue-400/70', color: '#2563eb' },
    review: { label: '审查', icon: Eye,    dot: 'bg-amber-500',   text: 'text-amber-600 dark:text-amber-300',    border: 'border-amber-400/70 dark:border-amber-500/50',    ring: 'focus:ring-amber-500/50', chip: 'bg-amber-50 dark:bg-amber-500/10', gradient: 'bg-gradient-to-r from-amber-500/10 to-amber-500/5', hover: 'hover:border-amber-500/80 dark:hover:border-amber-400/70', color: '#d97706' },
    goal:   { label: '目标', icon: Target, dot: 'bg-violet-500',  text: 'text-violet-600 dark:text-violet-300',   border: 'border-violet-400/70 dark:border-violet-500/50',   ring: 'focus:ring-violet-500/50', chip: 'bg-violet-50 dark:bg-violet-500/10', gradient: 'bg-gradient-to-r from-violet-500/10 to-violet-500/5', hover: 'hover:border-violet-500/80 dark:hover:border-violet-400/70', color: '#7c3aed' },
    deep:   { label: '深度', icon: Brain,  dot: 'bg-red-500',     text: 'text-red-600 dark:text-red-300',         border: 'border-red-400/70 dark:border-red-500/50',         ring: 'focus:ring-red-500/50',   chip: 'bg-red-50 dark:bg-red-500/10', gradient: 'bg-gradient-to-r from-red-500/10 to-red-500/5', hover: 'hover:border-red-500/80 dark:hover:border-red-400/70', color: '#dc2626' },
}

const RISK_CONFIG: Record<number, { label: string; desc: string; color: string; dot: string; text: string; border: string; ring: string; chip: string; bg: string; hover: string }> = {
    '-1': { label: '禁止执行',   desc: '命令将被拒绝执行',   hover: 'hover:border-gray-500/80 dark:hover:border-gray-400/70', color: '#9ca3af', dot: 'bg-gray-400',    text: 'text-gray-500 dark:text-gray-400',    border: 'border-gray-300/70 dark:border-gray-600',        ring: 'focus:ring-gray-500/50',   chip: 'bg-gray-100 dark:bg-gray-500/10',      bg: 'bg-gradient-to-r from-gray-500/10 to-gray-500/5' },
    0:  { label: '仅限安全操作', desc: '仅允许读取与无副作用查询', hover: 'hover:border-green-500/80 dark:hover:border-green-400/70', color: '#22c55e', dot: 'bg-green-500', text: 'text-green-600 dark:text-green-300',  border: 'border-green-400/70 dark:border-green-500/50',   ring: 'focus:ring-green-500/50',   chip: 'bg-green-50 dark:bg-green-500/10',     bg: 'bg-gradient-to-r from-green-500/10 to-green-500/5' },
    1:  { label: '允许常规操作', desc: '常规操作放行，危险指令需确认', hover: 'hover:border-lime-500/80 dark:hover:border-lime-400/70', color: '#84cc16', dot: 'bg-lime-500',  text: 'text-lime-700 dark:text-lime-300',    border: 'border-lime-400/70 dark:border-lime-500/50',     ring: 'focus:ring-lime-500/50',    chip: 'bg-lime-50 dark:bg-lime-500/10',       bg: 'bg-gradient-to-r from-lime-500/10 to-lime-500/5' },
    2:  { label: '允许敏感操作', desc: '允许敏感操作，涉及修改需谨慎', hover: 'hover:border-amber-500/80 dark:hover:border-amber-400/70', color: '#f59e0b', dot: 'bg-amber-500', text: 'text-amber-600 dark:text-amber-300',  border: 'border-amber-400/70 dark:border-amber-500/50',   ring: 'focus:ring-amber-500/50',   chip: 'bg-amber-50 dark:bg-amber-500/10',     bg: 'bg-gradient-to-r from-amber-500/10 to-amber-500/5' },
    3:  { label: '始终允许',     desc: '允许所有操作，含高风险指令', hover: 'hover:border-orange-500/80 dark:hover:border-orange-400/70', color: '#f97316', dot: 'bg-orange-500', text: 'text-orange-600 dark:text-orange-300', border: 'border-orange-400/70 dark:border-orange-500/50', ring: 'focus:ring-orange-500/50',  chip: 'bg-orange-50 dark:bg-orange-500/10',   bg: 'bg-gradient-to-r from-orange-500/10 to-orange-500/5' },
}

function getOrCreateDeviceId(): string {
    const KEY = '__device_id__';
    let deviceId = localStorage.getItem(KEY);
    if (!deviceId) {
        deviceId = crypto.randomUUID();
        localStorage.setItem(KEY, deviceId);
    }
    return deviceId;
}

// ===================== 节流工具 =====================
function createThrottledUpdater(updater: (value: any) => void) {
    let rafId: number | null = null
    let latestValue: any = undefined

    const flush = () => {
        if (rafId !== null) {
            cancelAnimationFrame(rafId)
            rafId = null
        }
        if (latestValue !== undefined) {
            updater(latestValue)
            latestValue = undefined
        }
    }

    const update = (value: any) => {
        latestValue = value
        if (rafId === null) {
            rafId = requestAnimationFrame(() => {
                updater(latestValue)
                latestValue = undefined
                rafId = null
            })
        }
    }

    return {update, flush}
}

// ===================== 消息列表子组件 =====================
const MessageList = React.memo(
    ({
         messages,
         isStreaming,
         isBackendGenerating,
         showLoadMore,
         onLoadMore,
         historyLoading,
         onDelete,
         isBackendSession,
     }: any) => {
        const lastIdx = messages.length - 1
        const lastMsg = messages[lastIdx]
        // const secondLastMsg = lastIdx > 0 ? messages[lastIdx - 1] : null
        const isAssistantStreaming = isStreaming && lastMsg?.role === 'assistant'
        const showTyping =
            (isAssistantStreaming && !lastMsg?.content) || isBackendGenerating

        // 构建 toolCallId -> tool结果 的映射
        const toolResultsMap = useMemo(() => {
            const map: Record<string, string> = {}
            for (const msg of messages) {
                if (msg.role === 'tool' && msg.toolCallId) {
                    map[msg.toolCallId] = msg.content || ''
                }
            }
            return map
        }, [messages])

        // 被 assistant 的 toolCalls 消费的 toolCallId 集合
        const consumedToolCallIds = useMemo(() => {
            const set = new Set<string>()
            for (const msg of messages) {
                if (msg.role === 'assistant' && msg.toolCalls) {
                    for (const tc of msg.toolCalls) {
                        if (tc.id) set.add(tc.id)
                    }
                }
            }
            return set
        }, [messages])

        // 全局 accordion 状态：整个对话同一时间只展开一个 ToolCallSection
        const [expandedMsgId, setExpandedMsgId] = useState<string | null>(null)

        if (messages.length === 0 && !isBackendGenerating) {
            return (
                <div className="flex flex-col items-center justify-center h-full text-center px-4 sm:px-6">
                    <div
                        className="w-20 h-20 rounded-full bg-black dark:bg-white flex items-center justify-center mb-4 sm:mb-5 shadow-lg overflow-hidden">
                        <MatrixLogo size="xl" className="w-10 h-10 sm:w-16 sm:h-16 text-white dark:text-gray-900"/>
                    </div>
                    <h2 className="text-lg sm:text-xl font-bold text-gray-800 dark:text-white mb-2">
                        Matrix
                    </h2>
                    <p className="text-[11px] sm:text-xs text-gray-500 dark:text-gray-400 max-w-xs sm:max-w-sm md:max-w-md leading-relaxed">
                        {isBackendSession ? '会话暂无消息记录' : 'Be water, my friend.'}
                    </p>
                </div>
            )
        }

        return (
            <>
                {/* 加载更多按钮 */}
                {showLoadMore && (
                    <div className="flex justify-center py-2">
                        <button
                            onClick={onLoadMore}
                            disabled={historyLoading}
                            className="flex items-center gap-1.5 rounded-lg px-3 sm:px-4 py-1.5 sm:py-2 text-[10px] sm:text-[11px] text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.04] transition-colors disabled:opacity-50"
                            aria-label="加载更早的消息"
                        >
                            {historyLoading ? (
                                <Loader2 className="w-3 h-3 sm:w-3.5 sm:h-3.5 animate-spin"/>
                            ) : (
                                <ChevronUp className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                            )}
                            加载更早的消息
                        </button>
                    </div>
                )}

                {/* 已完成的消息（不包括最后一条） */}
                {messages.slice(0, -1).map((msg: Message) => {
                    // 跳过已被 assistant toolCalls 消费的 tool 消息
                    if (msg.role === 'tool' && msg.toolCallId && consumedToolCallIds.has(msg.toolCallId)) {
                        return null
                    }
                    return (
                        <div key={msg.id}>
                            <MessageBubble
                                message={msg}
                                isStreaming={false}
                                onDelete={() => onDelete(msg.id)}
                                toolResultsMap={toolResultsMap}
                                isToolCallExpanded={expandedMsgId === msg.id}
                                onToggleToolCall={() => setExpandedMsgId(expandedMsgId === msg.id ? null : msg.id)}
                            />
                        </div>
                    )
                })}

                {/* 最后一条消息（流式更新目标） */}
                {lastMsg && (() => {
                    // 跳过已被 assistant toolCalls 消费的 tool 消息
                    if (lastMsg.role === 'tool' && lastMsg.toolCallId && consumedToolCallIds.has(lastMsg.toolCallId)) {
                        return null
                    }
                    return (
                        <div key={lastMsg.id}>
                            <MessageBubble
                                message={lastMsg}
                                isStreaming={isAssistantStreaming}
                                onDelete={() => onDelete(lastMsg.id)}
                                toolResultsMap={toolResultsMap}
                                isToolCallExpanded={expandedMsgId === lastMsg.id}
                                onToggleToolCall={() => setExpandedMsgId(expandedMsgId === lastMsg.id ? null : lastMsg.id)}
                            />
                        </div>
                    )
                })()}

                {/* 打字指示器 */}
                {showTyping && <TypingIndicator/>}
            </>
        )
    }
)

// ===================== 主组件 =====================

// ===================== 项目路径输入组件（编程模式） =====================
const ProjectPathInput = () => {
    const projectPathHistory = useChatStore((s) => s.projectPathHistory)
    const itemPath = useChatStore((s) => s.itemPath)
    const setItemPath = useChatStore((s) => s.setItemPath)
    const addProjectPath = useChatStore((s) => s.addProjectPath)
    const removeProjectPath = useChatStore((s) => s.removeProjectPath)
    const [dropdownOpen, setDropdownOpen] = useState(false)
    const containerRef = useRef<HTMLDivElement>(null)

    const currentClientId = useChatStore((s) => s.currentClientId)
    const setCurrentClientId = useChatStore((s) => s.setCurrentClientId)
    const [clientList, setClientList] = useState<{ clientId: string; name: string }[]>([])
    const [clientLoading, setClientLoading] = useState(false)

    // Fetch client list on mount
    useEffect(() => {
        const fetchClients = async () => {
            setClientLoading(true)
            try {
                const {getAuthHeaders, getApiBaseUrl} = await import("../utils/apiClient")
                const API_BASE = getApiBaseUrl()
                const res = await fetch(API_BASE + "/client/list", {
                    headers: {...getAuthHeaders(), "Content-Type": "application/json"},
                })
                if (res.ok) {
                    const body = await res.json()
                    if (body?.data && Array.isArray(body.data)) {
                        const list = body.data.map((item: string) => JSON.parse(item))
                        setClientList(list)
                    }
                }
            } catch (e) {
                console.error("Failed to fetch client list", e)
            } finally {
                setClientLoading(false)
            }
        }
        fetchClients()
    }, [])

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setDropdownOpen(false)
            }
        }
        document.addEventListener('mousedown', handleClickOutside)
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [])

    const handleFocus = () => {
        if (projectPathHistory.length > 0) {
            setDropdownOpen(true)
        }
    }

    const handleBlur = () => {
        if (itemPath.trim()) {
            addProjectPath(itemPath.trim())
        }
    }

    const handleSelectHistory = (path: string) => {
        setItemPath(path)
        setDropdownOpen(false)
    }

    const handleClientChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
        setCurrentClientId(e.target.value)
    }

    return (
        <div className="bg-white dark:bg-[#18181B]/70">
            <div className="max-w-4xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl mx-auto px-3 sm:px-4 py-1 sm:py-1.5">
                <div className="flex items-center gap-2 text-xs sm:text-sm text-gray-600 dark:text-gray-400">
                    {/* 终端选择下拉框 */}
                    <div className="flex items-center gap-1 flex-shrink-0">
                        <Monitor className="w-3.5 h-3.5 text-gray-400"/>
                        <select
                            value={currentClientId}
                            onChange={handleClientChange}
                            disabled={clientLoading}
                            className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181B]/70 px-1.5 sm:px-2 py-1 text-[10px] sm:text-xs font-medium text-gray-700 dark:text-gray-200 outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all cursor-pointer max-w-[120px]"
                            aria-label="选择终端"
                        >
                            <option value="">全部终端</option>
                            {clientList.map((client) => (
                                <option key={client.clientId} value={client.clientId}>
                                    {client.name.length > 18 ? client.name.slice(0, 18) + "..." : client.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <Folder className="w-3.5 h-3.5 text-gray-400 flex-shrink-0"/>
                    <div className="relative flex-1" ref={containerRef}>
                        <input
                            type="text"
                            value={itemPath}
                            onChange={(e) => setItemPath(e.target.value)}
                            onFocus={handleFocus}
                            onBlur={handleBlur}
                            placeholder="输入或粘贴项目绝对路径，如 /Users/xxx/project"
                            className="w-full overflow-x-auto whitespace-nowrap rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 px-2 py-1.5 text-xs text-gray-800 dark:text-gray-200 outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all placeholder-gray-400 placeholder-gray-500 dark:placeholder-gray-600"
                        />
                        {dropdownOpen && projectPathHistory.length > 0 && (
                            <div
                                className="absolute left-0 right-0 bottom-full mb-1 z-50 rounded-lg border border-gray-200 dark:border-white/[0.08] bg-white dark:bg-[#1c1c20]/95 dark:backdrop-blur-2xl shadow-2xl py-1 max-h-[185px] overflow-y-auto">
                                {projectPathHistory.map((path: string) => (
                                    <div
                                        key={path}
                                        onMouseDown={(e) => e.preventDefault()}
                                        onClick={() => handleSelectHistory(path)}
                                        className="flex items-center justify-between px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/[0.04] cursor-pointer transition-colors"
                                    >
                                        <span className="overflow-x-auto whitespace-nowrap flex-1 mr-2">{path}</span>
                                        <button
                                            onMouseDown={(e) => e.stopPropagation()}
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                removeProjectPath(path)
                                            }}
                                            className="flex-shrink-0 rounded p-1 hover:bg-gray-200 dark:hover:bg-white/[0.08] text-gray-400 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                                            aria-label="移除路径"
                                        >
                                            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none"
                                                 stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                                                 strokeLinejoin="round">
                                                <line x1="18" y1="6" x2="6" y2="18"/>
                                                <line x1="6" y1="6" x2="18" y2="18"/>
                                            </svg>
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    {itemPath && (
                        <button
                            onClick={() => setItemPath('')}
                            className="rounded-lg px-2 py-1.5 text-xs font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                        >
                            清除
                        </button>
                    )}
                </div>
            </div>
        </div>
    )
}

export default function ChatArea() {
    // 确保设备 ID 已生成（非必须，但可以提前准备）
    useEffect(() => {
        getOrCreateDeviceId();
    }, []);

    const currentSessionId = useChatStore((s) => s.currentSessionId)
    const sessions = useChatStore((s) => s.sessions)
    const addMessage = useChatStore((s) => s.addMessage)
    const addHistoryMessage = useChatStore((s) => s.addHistoryMessage)
    const setStreaming = useChatStore((s) => s.setStreaming)
    const setBackendSessionId = useChatStore((s) => s.setBackendSessionId)
    const isStreaming = useChatStore((s) => s.isStreaming)
    const isBackendGenerating = useChatStore((s) => s.isBackendGenerating)
    const setBackendGenerating = useChatStore((s) => s.setBackendGenerating)
    const backendStatus = useChatStore((s) => s.backendStatus)
    const setBackendStatus = useChatStore((s) => s.setBackendStatus)
    const toggleSidebar = useChatStore((s) => s.toggleSidebar)
    const createSession = useChatStore((s) => s.createSession)
    const historyMessages = useChatStore((s) => s.historyMessages)
    const historyLoading = useChatStore((s) => s.historyLoading)
    const historyLoaded = useChatStore((s) => s.historyLoaded)
    const historyPage = useChatStore((s) => s.historyPage)
    const historyPages = useChatStore((s) => s.historyPages)
    const loadMoreHistoryMessages = useChatStore((s) => s.loadMoreHistoryMessages)
    const fetchHistoryMessages = useChatStore((s) => s.fetchHistoryMessages)
    const deleteHistoryMessage = useChatStore((s) => s.deleteHistoryMessage)
    // const removeLastAssistantMessage = useChatStore((s) => s.removeLastAssistantMessage)
    // const removeLastHistoryMessage = useChatStore((s) => s.removeLastHistoryMessage)
    const apiKey = useApiKeyStore((s) => s.apiKey)
    const backendSessionList = useChatStore((s) => s.backendSessionList)
    const markdownEnabled = useChatStore((s) => s.markdownEnabled)
    const setMarkdownEnabled = useChatStore((s) => s.setMarkdownEnabled)
    const messageFilterMode = useChatStore((s) => s.messageFilterMode)
    const setMessageFilterMode = useChatStore((s) => s.setMessageFilterMode)
    const currentAgentName = useChatStore((s) => s.currentAgentName)
    const agentList = useChatStore((s) => s.agentList)
    const agentListLoading = useChatStore((s) => s.agentListLoading)
    const fetchAgentList = useChatStore((s) => s.fetchAgentList)
    const setCurrentAgent = useChatStore((s) => s.setCurrentAgent)
    const updateAuthLevel = useChatStore((s) => s.updateAuthLevel)
    const updateAgent = useChatStore((s) => s.updateAgent)
    const userAuthLevel = useChatStore((s) => s.userAuthLevel)
    const setUserAuthLevel = useChatStore((s) => s.setUserAuthLevel)
    const fetchUserAuthLevel = useChatStore((s) => s.fetchUserAuthLevel)
    const pattern = useChatStore((s) => s.pattern)
    const setPattern = useChatStore((s) => s.setPattern)
    const hook = useChatStore((s) => s.hook)
    const setHook = useChatStore((s) => s.setHook)
    const [showApiKeyModal, setShowApiKeyModal] = useState(false)

    // Task Auth modal state
    const taskList = useTaskAuthStore((s) => s.taskList)
    const modalOpen = useTaskAuthStore((s) => s.modalOpen)
    const setModalOpen = useTaskAuthStore((s) => s.setModalOpen)
    const closeModal = useTaskAuthStore((s) => s.closeModal)

    const isBackendSession = isBackendSessionId(currentSessionId)
    const currentSession = sessions.find((s) => s.id === currentSessionId)
    const localMessages = currentSession?.messages ?? []
    const messages = isBackendSession ? historyMessages : localMessages

    const messagesContainerRef = useRef<HTMLDivElement>(null)
    const abortRef = useRef<AbortController | null>(null)
    const prevScrollHeightRef = useRef<number>(0)

    // ========== 滚动逻辑 ==========
    const userHasScrolledUpRef = useRef(false)
    const isAutoScrollingRef = useRef(false)

    const scrollToBottom = useCallback((smooth = false) => {
        const container = messagesContainerRef.current
        if (!container) return

        isAutoScrollingRef.current = true
        if (smooth) {
            container.scrollTo({top: container.scrollHeight, behavior: 'smooth'})
        } else {
            container.scrollTop = container.scrollHeight
        }
        // 延迟重置自动滚动标志，避免这次滚动触发的 scroll 事件误判用户上滑
        setTimeout(() => {
            isAutoScrollingRef.current = false
        }, 100)
    }, [])

    const ensureScrollBottom = useCallback((maxRetries = 3) => {
        if (userHasScrolledUpRef.current) return
        let retryCount = 0
        const tryScroll = () => {
            if (retryCount >= maxRetries || userHasScrolledUpRef.current) return
            requestAnimationFrame(() => {
                if (!userHasScrolledUpRef.current) {
                    scrollToBottom(false)
                }
                retryCount++
                tryScroll()
            })
        }
        tryScroll()
    }, [scrollToBottom])

    // ① 会话切换/刷新时，同步重置用户上滑标志
    useLayoutEffect(() => {
        userHasScrolledUpRef.current = false
    }, [currentSessionId])

    // ② 核心自动滚动：messages 变化或流式状态变化时，若未上滑则滚底
    useLayoutEffect(() => {
        if (!userHasScrolledUpRef.current && (messages.length > 0 || isStreaming)) {
            ensureScrollBottom(3)
        }
    }, [messages, isStreaming, ensureScrollBottom])

    // ③ 初始加载完成后，多次重试确保滚底
    useEffect(() => {
        if (historyLoaded && messages.length > 0 && !userHasScrolledUpRef.current) {
            ensureScrollBottom(5)
        }
    }, [historyLoaded, messages.length, ensureScrollBottom])

    //④ MutationObserver监听子节点变化（捕获 framer-motion动画 style变更），确保 scrollHeight稳定后自动滚底
    useEffect(() => {
        const container = messagesContainerRef.current
        if (!container) return

        let mutationTimer: number | null = null

        const observer = new MutationObserver(() => {
            //防抖：DOM稳定后100ms再滚动，避免 framer-motion动画帧频繁触发
            if (mutationTimer) clearTimeout(mutationTimer)
            mutationTimer = window.setTimeout(() => {
                if (!userHasScrolledUpRef.current) {
                    scrollToBottom(false)
                }
            }, 33)
        })

        observer.observe(container, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ["style"], //捕获 framer-motion的 style属性变化
        })

        return () => {
            observer.disconnect()
            if (mutationTimer) clearTimeout(mutationTimer)
        }
    }, [scrollToBottom])

    // ⑤ scroll 事件：仅当用户滚回底部时恢复自动跟随
    useEffect(() => {
        const container = messagesContainerRef.current
        if (!container) return

        const handleScroll = () => {
            const dist = container.scrollHeight - container.scrollTop - container.clientHeight
            if (dist < 2) {
                userHasScrolledUpRef.current = false
            }
        }

        container.addEventListener('scroll', handleScroll, {passive: true})
        return () => container.removeEventListener('scroll', handleScroll)
    }, [])

    // ⑥ 监听用户真实的交互动作（鼠标滚轮、触摸滑动），标记用户主动上滑
    useEffect(() => {
        const container = messagesContainerRef.current
        if (!container) return

        const markUserScroll = () => {
            const container = messagesContainerRef.current
            if (!container) return
            const dist = container.scrollHeight - container.scrollTop - container.clientHeight
            //仅距离底部超过15px时才标记为用户主动上滑，避免微小抖动误触
            if (dist > 15) {
                userHasScrolledUpRef.current = true
            }
        }

        container.addEventListener('wheel', markUserScroll, {passive: true})
        container.addEventListener('touchmove', markUserScroll, {passive: true})

        return () => {
            container.removeEventListener('wheel', markUserScroll)
            container.removeEventListener('touchmove', markUserScroll)
        }
    }, [])

    // ⑦ 消息折叠：点击消息列表空白区域全部折叠
    useEffect(() => {
        const container = messagesContainerRef.current
        if (!container) return

        const handleMouseDown = (e: MouseEvent) => {
            const state = useChatStore.getState()
            if (state.expandedMsgIds.length === 0) return
            const target = e.target as HTMLElement
            // 白名单：点击 bubble actions（复制/删除按钮）不折叠
            if (target.closest('[data-bubble-actions="true"]')) return
            // 白名单：点击任一展开的 flag/error 消息容器内部不折叠
            for (const id of state.expandedMsgIds) {
                const el = container.querySelector(`[data-expandable-msg-id="${id}"]`)
                if (el && el.contains(target)) return
            }
            state.collapseAllMsg()
        }

        container.addEventListener('mousedown', handleMouseDown)
        return () => container.removeEventListener('mousedown', handleMouseDown)
    }, [])

    // 加载更多历史消息时保持滚动位置
    useEffect(() => {
        if (isBackendSession && historyPage > 1 && prevScrollHeightRef.current > 0) {
            const container = messagesContainerRef.current
            if (container) container.scrollTop = container.scrollHeight - prevScrollHeightRef.current
        }
    }, [historyMessages, historyPage, isBackendSession])

    useEffect(() => {
        fetchAgentList()
        fetchUserAuthLevel()
    }, [fetchAgentList, fetchUserAuthLevel])

    const pollingTimerRef = useRef<number | null>(null)

    const stopPolling = useCallback(() => {
        if (pollingTimerRef.current !== null) {
            clearTimeout(pollingTimerRef.current)
            pollingTimerRef.current = null
        }
    }, [])

    const checkAndStartPolling = useCallback(() => {
        stopPolling()
        const apiKey = useApiKeyStore.getState().apiKey
        if (!apiKey) return
        const sid = useChatStore.getState().currentSessionId
        if (!sid || !isBackendSessionId(sid)) return
        fetch(`${API_BASE}/session/isCompletions/${sid}`, {
            headers: {...getAuthHeaders()},
        })
            .then((r) => r.json())
            .then((body) => {
                const isCompleting = body?.data?.isConversation === true
                const currentSid = useChatStore.getState().currentSessionId
                if (!currentSid || !isBackendSessionId(currentSid)) {
                    stopPolling()
                    return
                }

                fetchHistoryMessages(Number(currentSid), 1)

                if (isCompleting) {
                    setBackendGenerating(true)
                    setBackendStatus(body?.data?.status || '')
                    pollingTimerRef.current = window.setTimeout(() => {
                        checkAndStartPolling()
                    }, 1000)
                } else {
                    setBackendGenerating(false)
                    setBackendStatus('')
                    stopPolling()
                }
            })
            .catch(() => {
                stopPolling()
                setBackendGenerating(false)
                setBackendStatus('')
            })
    }, [setBackendGenerating, setBackendStatus, fetchHistoryMessages, stopPolling])

    useEffect(() => {
        checkAndStartPolling()
        return () => {
            stopPolling()
        }
    }, [checkAndStartPolling])

    useEffect(() => {
        if (currentSessionId && isBackendSessionId(currentSessionId)) {
            checkAndStartPolling()
        } else {
            stopPolling()
            setBackendGenerating(false)
            setBackendStatus('')
        }
        return () => {
            stopPolling()
        }
    }, [currentSessionId, checkAndStartPolling, setBackendGenerating, setBackendStatus])

    useEffect(() => {
        if (currentSessionId && isBackendSessionId(currentSessionId) && !historyLoaded && !historyLoading) {
            fetchHistoryMessages(Number(currentSessionId), 1)
        }
    }, [currentSessionId, fetchHistoryMessages, historyLoaded, historyLoading])

    useEffect(() => {
        if (currentSessionId) return
        createSession()
    }, [currentSessionId, createSession])

    // ===================== 流式请求 =====================
    const startStreaming = useCallback(
        (text: string) => {
            if (!apiKey) {
                setShowApiKeyModal(true)
                return
            }
            userHasScrolledUpRef.current = false
            scrollToBottom(false)
            setStreaming(true)
            setBackendGenerating(true)
            setBackendStatus('')
            const controller = new AbortController()
            abortRef.current = controller

            const {update: throttledContent, flush: flushContent} = createThrottledUpdater((content: string) => {
                const state = useChatStore.getState()
                const isBack = isBackendSessionId(state.currentSessionId)
                if (isBack) {
                    state.updateLastHistoryMessage(content)
                } else {
                    state.updateLastAssistantMessage(content)
                }
            })

            const {update: throttledReasoning, flush: flushReasoning} = createThrottledUpdater((reasoning: string) => {
                const state = useChatStore.getState()
                const isBack = isBackendSessionId(state.currentSessionId)
                if (isBack) {
                    state.updateLastHistoryReasoning(reasoning)
                } else {
                    state.updateLastAssistantReasoning(reasoning)
                }
            })

            let toolCallsBatch: ToolCall[] = []
            let toolCallsRaf: number | null = null

            const flushToolCallsBatch = () => {
                if (toolCallsBatch.length === 0) return
                const batch = toolCallsBatch
                toolCallsBatch = []
                const state = useChatStore.getState()
                const isBack = isBackendSessionId(state.currentSessionId)
                if (isBack) {
                    state.updateLastHistoryToolCalls(batch)
                } else {
                    state.updateLastAssistantToolCalls(batch)
                }
            }

            const handleToolCalls = (newChunks: ToolCall[]) => {
                toolCallsBatch.push(...newChunks)
                if (toolCallsRaf === null) {
                    toolCallsRaf = requestAnimationFrame(() => {
                        flushToolCallsBatch()
                        toolCallsRaf = null
                    })
                }
            }

            chatCompletion(
                text,
                {
                    onContent: (content) => throttledContent(content),
                    onReasoning: (reasoning) => throttledReasoning(reasoning),
                    onToolCalls: (toolCalls) => handleToolCalls(toolCalls),
                    onToolMessage: (toolCallId, content) => {
                        const state = useChatStore.getState()
                        const isBack = isBackendSessionId(state.currentSessionId)
                        if (isBack) {
                            state.addHistoryToolMessage(toolCallId, content)
                        } else {
                            state.addToolMessage(toolCallId, content)
                        }
                    },
                    onSessionInit: (sessionId) => {
                        setBackendSessionId(sessionId)
                        //立即切换 currentSessionId到后端会话 ID，确保侧边栏选中该会话
                        const state = useChatStore.getState()
                        const localSid = state.currentSessionId
                        if (localSid && !isBackendSessionId(localSid)) {
                            useChatStore.setState({currentSessionId: String(sessionId)})
                        }
                        useChatStore.getState().fetchSessions(1).then(() => {
                            const _st = useChatStore.getState()
                            const _authLevel = _st.userAuthLevel
                            if (_authLevel !== 0) {
                                _st.updateAuthLevel(sessionId, _authLevel)
                            }
                        })
                        checkAndStartPolling()
                    },
                    onDone: () => {
                        // 清除引用的会话
                        useChatStore.getState().clearReferencedSessions();
                        flushContent();
                        flushReasoning();
                        flushToolCallsBatch();
                        setStreaming(false)
                        setBackendGenerating(false)
                        setBackendStatus('')
                        abortRef.current = null

                        //切换本地会话到后端会话，并删除已转换的本地会话（避免脏数据残留）
                        const _st = useChatStore.getState();
                        const _sid = _st.currentSessionId;
                        if (_sid && !isBackendSessionId(_sid)) {
                            const _local = _st.sessions.find(s => s.id === _sid);
                            if (_local?.backendSessionId) {
                                const _bid = _local.backendSessionId;
                                //先更新currentSessionId，保留本地会话条目在sessions中（防止空状态闪烁）
                                const _sessions = _st.sessions.map(s =>
                                    s.id === _sid ? {...s, id: String(_bid), backendSessionId: _bid} : s
                                );
                                useChatStore.setState({
                                    currentSessionId: String(_bid),
                                    sessions: _sessions,
                                    historyMessages: _local.messages,
                                    historyLoaded: true,
                                });
                                //等fetchSessions返回后再从sessions中移除本地会话（此时backendSessionList已有该会话）
                                _st.fetchSessions(1).then(() => {
                                    const st = useChatStore.getState();
                                    useChatStore.setState({
                                        sessions: st.sessions.filter(s => s.id !== String(_bid)),
                                    });
                                });
                            }
                        }

                        //自动刷新一次当前会话消息，确保数据完整
                        const finalState = useChatStore.getState();
                        const finalSid = finalState.currentSessionId;
                        if (finalSid && isBackendSessionId(finalSid)) {
                            fetchHistoryMessages(Number(finalSid), 1);
                            checkAndStartPolling()
                        }
                    },
                    onError: (msg) => {
                        // 清除引用的会话
                        useChatStore.getState().clearReferencedSessions();
                        flushToolCallsBatch();
                        useToastStore.getState().addToast({type: 'error', message: msg})
                        setStreaming(false)
                        setBackendGenerating(false)
                        setBackendStatus('')
                        abortRef.current = null
                    },
                },
                controller.signal
            ).then(() => {
                setStreaming(false)
                //切换本地会话到后端会话 (同 onDone逻辑)
                const _st = useChatStore.getState();
                const _sid = _st.currentSessionId;
                if (_sid && !isBackendSessionId(_sid)) {
                    const _local = _st.sessions.find(s => s.id === _sid);
                    if (_local?.backendSessionId) {
                        const _bid = _local.backendSessionId;
                        const _sessions = _st.sessions.map(s =>
                            s.id === _sid ? {...s, id: String(_bid), backendSessionId: _bid} : s
                        );
                        useChatStore.setState({
                            currentSessionId: String(_bid),
                            sessions: _sessions,
                            historyMessages: _local.messages,
                            historyLoaded: true,
                        });
                        //等fetchSessions返回后再从sessions中移除本地会话（此时backendSessionList已有该会话）
                        _st.fetchSessions(1).then(() => {
                            const st = useChatStore.getState();
                            useChatStore.setState({
                                sessions: st.sessions.filter(s => s.id !== String(_bid)),
                            });
                        });
                    }
                }
            })
        },
        [apiKey, setStreaming, setBackendGenerating, setBackendStatus, setBackendSessionId, scrollToBottom, fetchHistoryMessages, checkAndStartPolling]
    )

    const handleSend = useCallback(
        (text: string) => {
            if (!apiKey) {
                setShowApiKeyModal(true)
                return
            }
            if (!currentSessionId) createSession()
            const isBack = isBackendSessionId(useChatStore.getState().currentSessionId)
            if (isBack) {
                addHistoryMessage({role: 'user', content: text})
                addHistoryMessage({role: 'assistant', content: ''})
            } else {
                addMessage({role: 'user', content: text})
                addMessage({role: 'assistant', content: ''})
            }
            startStreaming(text)
        },
        [currentSessionId, apiKey, addMessage, addHistoryMessage, startStreaming, createSession]
    )

    const handleStop = useCallback(() => {
        abortRef.current?.abort()
        abortRef.current = null
        setStreaming(false)
        setBackendGenerating(false)
        setBackendStatus('')
        const apiKeyVal = useApiKeyStore.getState().apiKey
        if (apiKeyVal) {
            const sid = useChatStore.getState().currentSessionId
            if (sid) {
                fetch(`${API_BASE}/session/stop/${sid}`, {
                    method: 'GET',
                    headers: {...getAuthHeaders()},
                }).catch(() => {
                })
            }
        }
    }, [setStreaming, setBackendGenerating, setBackendStatus])

    const handleDelete = useCallback(
        (msgId: string) => {
            const state = useChatStore.getState()
            const sid = state.currentSessionId
            if (!sid) return
            if (isBackendSessionId(sid)) {
                const sidNum = Number(sid)
                const mid = Number(msgId)
                if (!isNaN(sidNum) && !isNaN(mid)) {
                    deleteHistoryMessage(sidNum, mid)
                }
            } else {
                useChatStore.setState((state) => ({
                    sessions: state.sessions.map((s) =>
                        s.id === state.currentSessionId
                            ? {...s, messages: s.messages.filter((m) => m.id !== msgId)}
                            : s
                    ),
                }))
            }
        },
        [deleteHistoryMessage]
    )


    const handleLoadMore = useCallback(() => {
        const container = messagesContainerRef.current
        prevScrollHeightRef.current = container?.scrollHeight ?? 0
        loadMoreHistoryMessages()
    }, [loadMoreHistoryMessages])

    const handleRefresh = useCallback(() => {
        const state = useChatStore.getState();
        const sid = state.currentSessionId;
        if (!sid || !isBackendSessionId(sid)) return;
        userHasScrolledUpRef.current = false;
        useChatStore.getState().selectBackendSession(Number(sid));
        // 追加一次立即滚动，以防万一
        setTimeout(() => scrollToBottom(false), 100);
    }, [scrollToBottom]);

    const showLoadMore =
        isBackendSession && historyLoaded && historyPage < historyPages && !historyLoading
    const isInitialLoading = isBackendSession && !historyLoaded && historyLoading


    // ===================== 模式映射 =====================
    // ===================== UI 渲染 =====================
    return (
        <div className="flex-1 flex flex-col h-full min-w-0">
            {/* Header */}
            <div
                className="flex items-center justify-between px-3 sm:px-4 pt-2.5 pb-1.5 bg-white dark:bg-[#18181B]">
                <div className="flex items-center gap-2 sm:gap-3 min-w-0">
                    <button
                        onClick={toggleSidebar}
                        aria-label="打开菜单"
                        className="md:hidden rounded-lg p-1.5 sm:p-2 hover:bg-gray-100 dark:hover:bg-white/[0.04] transition-colors"
                    >
                        <Menu className="w-5 h-5 text-gray-600 dark:text-gray-400"/>
                    </button>
                    {/* 对话标题 - 从 sub-header 移到此处 */}
                    <span className="font-medium text-gray-700 dark:text-gray-300 truncate max-w-[120px] xs:max-w-[160px] sm:max-w-[250px] md:max-w-[300px] lg:max-w-[400px] xl:max-w-[500px]">
                        {(() => {
                            const sid = currentSessionId
                            if (!sid) return '新对话'
                            if (isBackendSessionId(sid)) {
                                const session = backendSessionList.find((s) => s.id === Number(sid))
                                return session?.title || '新对话'
                            }
                            const localSession = sessions.find((s) => s.id === sid)
                            return localSession?.title || '新对话'
                        })()}
                    </span>
                    {/* 刷新按钮 */}
                    <button
                        onClick={handleRefresh}
                        disabled={historyLoading}
                        className="rounded-lg p-1.5 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-50"
                        aria-label="刷新当前会话消息"
                        title="刷新消息"
                    >
                        <RefreshCw className="w-4 h-4 text-gray-500 dark:text-gray-400"/>
                    </button>
                </div>
                <div className="flex items-center gap-1.5 sm:gap-2 flex-shrink-0">
                    {/* 待授权按钮 */}
                    <button
                        onClick={() => setModalOpen(true)}
                        aria-label="待授权"
                        className={
                            "flex items-center gap-1 sm:gap-1.5 rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium transition-colors " +
                            (taskList.length > 0
                                ? "bg-orange-50 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300 hover:bg-orange-100 dark:hover:bg-orange-900/50"
                                : "bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 hover:bg-green-100 dark:hover:bg-green-900/50")
                        }
                    >
                        <ShieldCheck className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                        <span className="hidden sm:inline">待授权</span>
                        {taskList.length > 0 && (
                            <span className="w-2 h-2 rounded-full bg-orange-500 animate-pulse"/>
                        )}
                    </button>
                    {/* 消息过滤模式切换 */}
                    <button
                        onClick={() => {
                            const newMode = messageFilterMode === 'all' ? 'chat' : 'all'
                            setMessageFilterMode(newMode)
                            const sid = useChatStore.getState().currentSessionId
                            if (sid && isBackendSessionId(sid)) {
                                fetchHistoryMessages(Number(sid), 1)
                            }
                        }}
                        aria-label="切换消息过滤模式"
                        className={
                            "flex items-center gap-1 sm:gap-1.5 rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium transition-colors " +
                            (messageFilterMode === 'all'
                                ? "bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900/50"
                                : "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700")
                        }
                    >
                        <MessageSquare className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                        <span className="hidden sm:inline">{messageFilterMode === 'all' ? "全部" : "对话"}</span>
                    </button>
                    {/* Markdown 切换 */}
                    <button
                        onClick={() => setMarkdownEnabled(!markdownEnabled)}
                        aria-label="切换 Markdown渲染"
                        className={
                            "flex items-center gap-1 sm:gap-1.5 rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium transition-colors " +
                            (markdownEnabled
                                ? "bg-purple-50 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 hover:bg-purple-100 dark:hover:bg-purple-900/50"
                                : "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700")
                        }
                    >
                        <FileText className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                        <span className="hidden sm:inline">{markdownEnabled ? "渲染" : "文本"}</span>
                    </button>
                    {/* API Key 设置 */}
                    <button
                        onClick={() => setShowApiKeyModal(true)}
                        aria-label="设置 API Key"
                        className={
                            "flex items-center gap-1 sm:gap-1.5 rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium transition-colors " +
                            (apiKey
                                ? "bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 hover:bg-green-100 dark:hover:bg-green-900/50"
                                : "bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900/50")
                        }
                    >
                        <Key className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                        <span className="hidden sm:inline">{apiKey ? "已设置" : "未设置"}</span>
                    </button>
                </div>
            </div>

            {/* Sub-header */}
            <div
                className="flex items-center justify-between px-3 sm:px-4 py-1.5 sm:py-2 bg-white dark:bg-[#18181B] text-xs sm:text-sm gap-2">
                <div className="flex items-center gap-2 min-w-0 flex-1">
                    {/* 执行中指示器 */}
                    {(isStreaming || isBackendGenerating) && (
                        <span className="flex items-center gap-1 sm:gap-1.5 text-[10px] sm:text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                            <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"/>
                            {backendStatus || '执行中'}
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0 ml-1 sm:ml-2">
                    {/* 允许交互 */}
                    <button
                        onClick={() => setHook(!hook)}
                        aria-label={`允许交互：${hook ? '已开启' : '已关闭'}`}
                        className={
                            "flex items-center gap-1 sm:gap-1.5 rounded-lg px-2 sm:px-3 py-1 sm:py-1.5 text-[10px] sm:text-xs font-medium transition-colors " +
                            (hook
                                ? "bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900/50"
                                : "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700")
                        }
                    >
                        <ArrowLeftRight className="w-3 h-3 sm:w-3.5 sm:h-3.5"/>
                        <span className="hidden sm:inline">{hook ? "交互（开启）" : "交互（关闭）"}</span>
                    </button>
                    {/* 模式选择 */}
                    <div
                        className={"relative flex items-stretch rounded-xl border overflow-hidden shadow-sm transition-all duration-300 ease-in-out hover:shadow-md hover:-translate-y-px " + MODE_CONFIG[pattern].gradient + " " + MODE_CONFIG[pattern].border + " " + MODE_CONFIG[pattern].hover}
                        title={`当前模式：${MODE_CONFIG[pattern].label}`}
                    >
                        <span className={"flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 " + MODE_CONFIG[pattern].chip}>
                            {(() => { const Icon = MODE_CONFIG[pattern].icon; return <Icon className={"w-3.5 h-3.5 " + MODE_CONFIG[pattern].text}/> })()}
                            <span className={"text-[10px] sm:text-xs font-extrabold tracking-tight " + MODE_CONFIG[pattern].text}>模式</span>
                        </span>
                        <select
                            value={pattern}
                            onChange={(e) => setPattern(e.target.value as Pattern)}
                            className={"pl-2 pr-6 py-1 text-[10px] sm:text-xs font-bold outline-none cursor-pointer transition-colors duration-300 ease-in-out focus:ring-0 focus:ring-inset bg-transparent " + MODE_CONFIG[pattern].ring + " " + MODE_CONFIG[pattern].text}
                            aria-label="模式选择"
                        >
                            <option value="skill" style={{ color: pattern === "skill" ? "#16a34a" : "#9ca3af", fontWeight: pattern === "skill" ? 700 : 400 }}>技能</option>
                            <option value="execute" style={{ color: pattern === "execute" ? "#0891b2" : "#9ca3af", fontWeight: pattern === "execute" ? 700 : 400 }}>执行</option>
                            <option value="plan" style={{ color: pattern === "plan" ? "#2563eb" : "#9ca3af", fontWeight: pattern === "plan" ? 700 : 400 }}>规划</option>
                            <option value="review" style={{ color: pattern === "review" ? "#d97706" : "#9ca3af", fontWeight: pattern === "review" ? 700 : 400 }}>审查</option>
                            <option value="goal" style={{ color: pattern === "goal" ? "#7c3aed" : "#9ca3af", fontWeight: pattern === "goal" ? 700 : 400 }}>目标</option>
                            <option value="deep" style={{ color: pattern === "deep" ? "#dc2626" : "#9ca3af", fontWeight: pattern === "deep" ? 700 : 400 }}>深度</option>
                        </select>
                    </div>
                    {/* 技能选择（仅在 skill 模式下显示） */}
                    {pattern === 'skill' && (
                        <div className="flex items-center gap-1 sm:gap-1.5">
                            {(() => {
                                const sid = currentSessionId
                                const isBack = sid && isBackendSessionId(sid)
                                const session = isBack ? backendSessionList.find((s) => s.id === Number(sid)) : null
                                const currentAgent = isBack ? (session?.agent ?? currentAgentName) : currentAgentName
                                return (
                                    <>
                                        <select
                                            value={currentAgent}
                                            onChange={(e) => {
                                                const val = e.target.value
                                                if (isBack && sid) {
                                                    const sidNum = Number(sid)
                                                    if (!isNaN(sidNum)) {
                                                        updateAgent(sidNum, val)
                                                    }
                                                }
                                                setCurrentAgent(val)
                                            }}
                                            className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181B]/70 px-1.5 sm:px-2 py-1 text-[10px] sm:text-xs font-medium text-gray-700 dark:text-gray-200 outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all cursor-pointer max-w-[80px] xs:max-w-[100px] sm:max-w-[140px] md:max-w-[180px] lg:max-w-[200px]"
                                        >
                                            {agentListLoading ? (
                                                <option value={currentAgent}>{currentAgent}</option>
                                            ) : agentList.length === 0 ? (
                                                <option value={currentAgent}>{currentAgent}</option>
                                            ) : (
                                                agentList.map((name) => (
                                                    <option key={name} value={name}>
                                                        {name}
                                                    </option>
                                                ))
                                            )}
                                        </select>
                                    </>
                                )
                            })()}
                        </div>
                    )}

                    {/* 授权等级选择 */}
                    <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0 ml-1 sm:ml-2">
                        {(() => {
                            const sid = currentSessionId
                            const isBack = sid && isBackendSessionId(sid)
                            const session = isBack ? backendSessionList.find((s) => s.id === Number(sid)) : null
                            const currentLevel = isBack ? (session?.authLevel ?? 0) : userAuthLevel
                            return (
                                <div
                                    className={"flex items-stretch rounded-xl border overflow-hidden shadow-sm transition-all duration-300 ease-in-out hover:shadow-md hover:-translate-y-px " + RISK_CONFIG[currentLevel].border + " " + RISK_CONFIG[currentLevel].hover}
                                    title={`授权等级：${RISK_CONFIG[currentLevel].label} —— ${RISK_CONFIG[currentLevel].desc}`}
                                >
                                    <span className={"flex items-center gap-1.5 pl-2.5 pr-1.5 py-1 " + RISK_CONFIG[currentLevel].chip}>
                                        <span className={"w-1.5 h-1.5 rounded-full " + RISK_CONFIG[currentLevel].dot}/>
                                        {/*<span className={"w-1.5 h-1.5 rounded-full animate-pulse " + RISK_CONFIG[currentLevel].dot}/>*/}
                                        <span className={"text-[10px] sm:text-xs font-bold " + RISK_CONFIG[currentLevel].text}>授权</span>
                                    </span>
                                    <select
                                        value={currentLevel}
                                        onChange={(e) => {
                                            const val = Number(e.target.value)
                                            if (isBack && sid) {
                                                const sidNum = Number(sid)
                                                if (!isNaN(sidNum)) {
                                                    updateAuthLevel(sidNum, val)
                                                }
                                            } else {
                                                setUserAuthLevel(val)
                                            }
                                        }}
                                        className={"pl-2 pr-3 py-1 text-[10px] sm:text-xs font-semibold outline-none cursor-pointer transition-colors duration-300 ease-in-out focus:ring-0 focus:ring-offset-0 dark:focus:ring-offset-[#18181B] hover:brightness-105 risk-level-select " + RISK_CONFIG[currentLevel].text + " " + RISK_CONFIG[currentLevel].ring + " " + RISK_CONFIG[currentLevel].bg}
                                    >
                                        <option value={-1} className="risk-opt risk-opt--1" style={{ fontWeight: currentLevel === -1 ? 700 : 400 }}>禁止执行</option>
                                        <option value={0} className="risk-opt risk-opt-0" style={{ fontWeight: currentLevel === 0 ? 700 : 400 }}>仅限安全操作</option>
                                        <option value={1} className="risk-opt risk-opt-1" style={{ fontWeight: currentLevel === 1 ? 700 : 400 }}>允许常规操作</option>
                                        <option value={2} className="risk-opt risk-opt-2" style={{ fontWeight: currentLevel === 2 ? 700 : 400 }}>允许敏感操作</option>
                                        <option value={3} className="risk-opt risk-opt-3" style={{ fontWeight: currentLevel === 3 ? 700 : 400 }}>始终允许</option>
                                    </select>
                                </div>
                            )
                        })()}
                    </div>
                </div>
            </div>

            {/* 消息容器 */}
            <div
                ref={messagesContainerRef}
                className="flex-1 overflow-y-auto overflow-x-hidden py-2 sm:py-3 space-y-0 mx-auto w-full max-w-4xl lg:max-w-5xl xl:max-w-6xl 2xl:max-w-7xl"
                role="list"
                aria-label="消息列表"
                tabIndex={-1}
            >
                {isInitialLoading ? (
                    <div className="flex items-center justify-center h-full">
                        <Loader2 className="w-6 h-6 animate-spin text-gray-400"/>
                    </div>
                ) : (
                    <MessageList
                        messages={messages}
                        isStreaming={isStreaming}
                        isBackendGenerating={isBackendGenerating}
                        showLoadMore={showLoadMore}
                        onLoadMore={handleLoadMore}
                        historyLoading={historyLoading}
                        onDelete={handleDelete}
                        isBackendSession={isBackendSession}
                    />
                )}
                {/* 此空 div 不再用于滚动，但保留作为其他用途的参考点 */}
                <div/>
            </div>

            {/* 输入栏 */}
            {/* 项目路径选择 */}
            {<ProjectPathInput/>}

            <InputBar onSend={handleSend} onStop={handleStop} hideTopBorder={true}/>

            {/* API Key 弹窗 */}
            <React.Suspense fallback={null}>
                <ApiKeyModal open={showApiKeyModal} onClose={() => setShowApiKeyModal(false)}/>
            </React.Suspense>
            <React.Suspense fallback={null}>
                <TaskAuthModal open={modalOpen} onClose={closeModal}/>
            </React.Suspense>
        </div>
    )
}