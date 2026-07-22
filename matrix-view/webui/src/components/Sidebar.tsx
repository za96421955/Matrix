import React, {useEffect, useCallback, useState, useRef} from 'react'
import {motion, AnimatePresence} from 'framer-motion'
import {
    MessageSquarePlus,
    MessageSquare,
    PanelLeftClose,
    PanelLeft,
    Bot,
    Loader2,
    ChevronDown,
    Pencil,
    Trash2,
    MoreHorizontal
} from 'lucide-react'
import {useChatStore} from '../store/chatStore'
import {useApiKeyStore} from '../store/apiKeyStore'
import ThemeToggle from './ThemeToggle'
import {useTaskAuthStore} from '../store/taskAuthStore'
import {useEffectStore} from '../store/effectStore'

const TaskAuthModal = React.lazy(() => import('./TaskAuthModal'))

type TimeGroup = 'today' | 'thisWeek' | 'earlier'

function getTimeGroup(dateStr: string): TimeGroup | null {
    if (!dateStr) return null
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return null
    const now = new Date()
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const weekAgo = new Date(todayStart.getTime() - 7 * 24 * 60 * 60 * 1000)
    if (d >= todayStart) return 'today'
    if (d >= weekAgo) return 'thisWeek'
    return 'earlier'
}

const GROUP_LABELS: Record<TimeGroup, string> = {
    today: '今天',
    thisWeek: '一周内',
    earlier: '更早'
}

// ===================== 时间格式化 =====================
/** 格式化会话时间：当天显示 HH:mm，非当天显示 MM-dd */
function formatSessionTime(dateStr: string): string {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return ''
    const now = new Date()
    const isToday = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
    if (isToday) {
        return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0')
    } else {
        return (d.getMonth() + 1).toString().padStart(2, '0') + '-' + d.getDate().toString().padStart(2, '0')
    }
}

// ===================== 可滑动会话项组件 =====================
function SwipeableSessionItem({
    session,
    isActive,
    isEditing,
    editValue,
    setEditValue,
    editInputRef,
    onSelect,
    onStartEditing,
    onEditKeyDown,
    onSaveEditing,
    onDelete,
}: {
    session: { id: number; title: string; updateTime: string };
    isActive: boolean;
    isEditing: boolean;
    editValue: string;
    setEditValue: (v: string) => void;
    editInputRef: React.RefObject<HTMLInputElement | null>;
    onSelect: (id: number) => void;
    onStartEditing: (id: number, title: string, e?: React.MouseEvent) => void;
    onEditKeyDown: (e: React.KeyboardEvent) => void;
    onSaveEditing: () => void;
    onDelete: (id: number) => Promise<void>;
    }) {
    const BUTTONS_WIDTH = 80
    const SWIPE_THRESHOLD = 50
    const CLICK_THRESHOLD = 10

    const [swipeX, setSwipeX] = useState(0)
    const [isSwiped, setIsSwiped] = useState(false)
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
    const touchStartX = useRef(0)
    const touchStartY = useRef(0)
    const baseSwipeX = useRef(0)
    const contentRef = useRef<HTMLDivElement>(null)
    const itemRef = useRef<HTMLDivElement>(null)
    const isDragging = useRef(false)
    const totalDragDistance = useRef(0)
    const isVerticalScroll = useRef(false)
    const swipeXRef = useRef(swipeX)
    useEffect(() => { swipeXRef.current = swipeX }, [swipeX])

    const isTouchHandled = useRef(false)
    const wasSwiped = useRef(false)

    const handleResetSwipe = useCallback(() => {
        setIsSwiped(false)
        setSwipeX(0)
        setShowDeleteConfirm(false)
        isDragging.current = false
        totalDragDistance.current = 0
        isVerticalScroll.current = false
    }, [])

    // Touch 事件
    const handleTouchStart = useCallback((e: React.TouchEvent) => {
        if (isEditing) return
        isTouchHandled.current = true
        isDragging.current = true
        touchStartX.current = e.touches[0].clientX
        touchStartY.current = e.touches[0].clientY
        baseSwipeX.current = swipeXRef.current
        totalDragDistance.current = 0
        isVerticalScroll.current = false
        if (contentRef.current) {
            contentRef.current.style.transition = 'none'
        }
    }, [isEditing])

    const handleTouchMove = useCallback((e: React.TouchEvent) => {
        if (!isDragging.current || isEditing) return
        const touch = e.touches[0]
        const dx = touch.clientX - touchStartX.current
        const dy = Math.abs(touch.clientY - touchStartY.current)
        const absDx = Math.abs(dx)

        if (!isVerticalScroll.current && dy > absDx && swipeXRef.current === 0) {
            isVerticalScroll.current = true
        }
        if (isVerticalScroll.current) return

        totalDragDistance.current = absDx
        const newX = Math.max(-BUTTONS_WIDTH, Math.min(0, baseSwipeX.current + dx))
        setSwipeX(newX)
    }, [isEditing])

    const handleTouchEnd = useCallback(() => {
        if (!isDragging.current) return
        isDragging.current = false
        isVerticalScroll.current = false
        if (contentRef.current) {
            contentRef.current.style.transition = 'transform 0.2s ease-out'
        }
        if (isEditing) return

        if (totalDragDistance.current < CLICK_THRESHOLD) {
            handleResetSwipe()
            setTimeout(() => { isTouchHandled.current = false }, 300)
            return
        }

        setSwipeX((prev) => {
            if (prev < -SWIPE_THRESHOLD) {
                setIsSwiped(true)
                wasSwiped.current = true
                return -BUTTONS_WIDTH
            } else {
                setIsSwiped(false)
                setShowDeleteConfirm(false)
                return 0
            }
        })
        setTimeout(() => { isTouchHandled.current = false }, 300)
    }, [isEditing, handleResetSwipe])

    // Mouse 事件（桌面端拖拽）
    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        if (e.button !== 0 || isEditing) return
        if (isTouchHandled.current) { isTouchHandled.current = false; return }
        isDragging.current = true
        touchStartX.current = e.clientX
        baseSwipeX.current = swipeXRef.current
        totalDragDistance.current = 0
        if (contentRef.current) {
            contentRef.current.style.transition = 'none'
        }

        const onMouseMove = (e: MouseEvent) => {
            if (!isDragging.current) return
            const dx = e.clientX - touchStartX.current
            totalDragDistance.current = Math.abs(dx)
            const newX = Math.max(-BUTTONS_WIDTH, Math.min(0, baseSwipeX.current + dx))
            setSwipeX(newX)
        }

        const onMouseUp = () => {
            if (!isDragging.current) return
            isDragging.current = false
            if (contentRef.current) {
                contentRef.current.style.transition = 'transform 0.2s ease-out'
            }
            if (!isEditing) {
                if (totalDragDistance.current < CLICK_THRESHOLD) {
                    handleResetSwipe()
                } else {
                    setSwipeX((prev) => {
                        if (prev < -SWIPE_THRESHOLD) {
                            setIsSwiped(true)
                            wasSwiped.current = true
                            return -BUTTONS_WIDTH
                        } else {
                            setIsSwiped(false)
                            setShowDeleteConfirm(false)
                            return 0
                        }
                    })
                }
            }
            document.removeEventListener('mousemove', onMouseMove)
            document.removeEventListener('mouseup', onMouseUp)
        }

        document.addEventListener('mousemove', onMouseMove)
        document.addEventListener('mouseup', onMouseUp)
    }, [isEditing, handleResetSwipe])


    const handleDeleteClick = (e: React.MouseEvent) => {
        e.stopPropagation()
        setShowDeleteConfirm(true)
    }

    const handleConfirmDelete = async (e: React.MouseEvent) => {
        e.stopPropagation()
        await onDelete(session.id)
        handleResetSwipe()
    }

    const handleCancelDelete = (e: React.MouseEvent) => {
        e.stopPropagation()
        setShowDeleteConfirm(false)
    }

    const handleRename = (e: React.MouseEvent) => {
        e.stopPropagation()
        onStartEditing(session.id, session.title, e)
        handleResetSwipe()
    }

    // 点击主内容区域
    const handleContentClick = () => {
        if (isEditing) return
        if (wasSwiped.current) { wasSwiped.current = false; return }
        if (isSwiped) {
            handleResetSwipe()
        } else {
            onSelect(session.id)
        }
    }

    // 点击外部区域还原滑动
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (isSwiped && itemRef.current && !itemRef.current.contains(e.target as Node)) {
                handleResetSwipe()
            }
        }
        document.addEventListener('mousedown', handleClickOutside)
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [isSwiped, handleResetSwipe])

    // ===== z-index overlay 架构 =====
    // 按钮层 (z-0) 始终位于 right:0，被内容层覆盖
    // 内容层 (z-10) 带背景色，translateX 左滑时露出按钮
    return (
        <div className="relative rounded-lg group" ref={itemRef}>
            {/* 右侧操作按钮 - z-0 在内容层下方，始终位于 right:0 */}
            <div
                className="absolute right-0 top-0 h-full flex items-center gap-0.5 pr-0.5 dark:bg-[#1c1c20] z-0"
                style={{ width: `${BUTTONS_WIDTH}px` }}
            >
                {showDeleteConfirm ? (
                    <>
                        <button
                            onClick={handleConfirmDelete}
                            className="flex items-center justify-center w-9 h-8 rounded-md bg-red-600/80 hover:bg-red-700/90 backdrop-blur-sm text-white transition-colors"
                            aria-label="确认删除"
                        >
                            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="20 6 9 17 4 12"/>
                            </svg>
                        </button>
                        <button
                            onClick={handleCancelDelete}
                            className="flex items-center justify-center w-9 h-8 rounded-md bg-gray-500/80 hover:bg-gray-500 text-white transition-colors"
                            aria-label="取消删除"
                        >
                            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                            </svg>
                        </button>
                    </>
                ) : (
                    <>
                        <button
                            onClick={handleRename}
                            className="flex items-center justify-center w-9 h-8 rounded-md bg-gray-500/80 hover:bg-gray-500 text-white transition-colors ml-1"
                            aria-label="重命名"
                        >
                            <Pencil className="w-4 h-4"/>
                        </button>
                        <button
                            onClick={handleDeleteClick}
                            className="flex items-center justify-center w-9 h-8 rounded-md bg-red-600 hover:bg-red-700 text-white transition-colors"
                            aria-label="删除"
                        >
                            <Trash2 className="w-4 h-4"/>
                        </button>
                    </>
                )}
            </div>

            {/* 主内容区域 - z-10 在按钮层上方，带背景色覆盖按钮，左滑时露出按钮 */}
            <div
                ref={contentRef}
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
                onMouseDown={handleMouseDown}
                style={{
                    transform: `translateX(${swipeX}px)`,
                    transition: 'transform 0.2s ease-out'
                }}
                className="relative z-10 w-full bg-gray-50 dark:bg-[#1c1c20] rounded-lg"
            >
                <div
                    onClick={handleContentClick}
                    onDoubleClick={() => !isEditing && onStartEditing(session.id, session.title)}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            handleContentClick()
                        }
                    }}
                    role="button"
                    tabIndex={0}
                    aria-current={isActive ? 'true' : undefined}
                    aria-label={"对话: " + session.title}
                    className={
                        'w-full flex flex-col gap-0.5 rounded-lg px-2.5 py-2 text-sm text-left transition-all cursor-pointer select-none ' +
                        (isActive
                            ? 'bg-blue-50 dark:bg-white/[0.08] text-blue-600 dark:text-blue-300 font-medium border-l-[3px] border-blue-400 dark:border-blue-500'
                            : 'text-gray-800 dark:text-gray-200 hover:bg-gray-200/80 dark:hover:bg-white/[0.10]')
                    }
                >
                    <div className="flex items-center gap-2 w-full">
                        {isEditing ? (
                            <div className="w-full">
                                <input
                                    ref={editInputRef}
                                    type="text"
                                    value={editValue}
                                    onChange={(e) => setEditValue(e.target.value)}
                                    onKeyDown={onEditKeyDown}
                                    onBlur={onSaveEditing}
                                    className="w-full min-w-0 rounded px-1.5 py-0.5 text-sm bg-white dark:bg-[#252529] border border-blue-400 dark:border-white/[0.12] outline-none text-gray-800 dark:text-gray-200"
                                    onClick={(e) => e.stopPropagation()}
                                />
                            </div>
                        ) : (
                            <>
                        <MessageSquare className="w-4 h-4 flex-shrink-0 opacity-60"/>
                                <span className="flex-1 truncate">{session.title}</span>
                                <span className="text-xs text-gray-500 flex-shrink-0 ml-auto group-hover:opacity-0 transition-opacity duration-300">{formatSessionTime(session.updateTime)}</span>
                            </>
                        )}
                    </div>
                    {/* ... 按钮 - 桌面端 hover 时显示，左滑后隐藏 */}
                    <div
                        className={"absolute right-2 top-1/2 -translate-y-1/2 z-20 transition-all duration-300 " +
                            (isEditing ? "opacity-0 pointer-events-none" : "") +
                            (swipeX < 0
                                ? "opacity-0 pointer-events-none"
                                : "opacity-0 group-hover:opacity-100")
                        }
                    >
                        <button
                            onClick={(e) => { e.stopPropagation(); setSwipeX(-BUTTONS_WIDTH); setIsSwiped(true); }}
                            className="w-7 h-7 flex items-center justify-center rounded-md bg-gray-100 dark:bg-gray-700/50 text-gray-600 hover:bg-gray-200 dark:hover:bg-gray-600/50 transition-all duration-300 ease-out"
                            aria-label="更多操作"
                        >
                            <MoreHorizontal size={16}/>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}

export default function Sidebar() {
    const backendSessionList = useChatStore((s) => s.backendSessionList)
    const currentSessionId = useChatStore((s) => s.currentSessionId)
    const sessionsLoading = useChatStore((s) => s.sessionsLoading)
    const sessionsLoaded = useChatStore((s) => s.sessionsLoaded)
    const sessionsPage = useChatStore((s) => s.sessionsPage)
    const sessionsTotal = useChatStore((s) => s.sessionsTotal)
    const sessionsTotalPages = useChatStore((s) => s.sessionsTotalPages)
    const selectBackendSession = useChatStore((s) => s.selectBackendSession)
    const sessions = useChatStore((s) => s.sessions)
    const selectSession = useChatStore((s) => s.selectSession)
    const clearCurrentSession = useChatStore((s) => s.clearCurrentSession)
    const fetchSessions = useChatStore((s) => s.fetchSessions)
    const loadMoreSessions = useChatStore((s) => s.loadMoreSessions)
    const updateBackendSessionTitle = useChatStore((s) => s.updateBackendSessionTitle)
    const deleteBackendSession = useChatStore((s) => s.deleteBackendSession)
    const sidebarOpen = useChatStore((s) => s.sidebarOpen)
    const toggleSidebar = useChatStore((s) => s.toggleSidebar)
    const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)
    const apiKey = useApiKeyStore((s) => s.apiKey)

    // Matrix rain toggle
    const matrixRainEnabled = useEffectStore((s) => s.matrixRainEnabled)
    const toggleMatrixRain = useEffectStore((s) => s.toggleMatrixRain)
    const isBackendGenerating = useChatStore((s) => s.isBackendGenerating)

    // Task Auth
    const modalOpen = useTaskAuthStore((s) => s.modalOpen)
    const closeModal = useTaskAuthStore((s) => s.closeModal)
    const startPolling = useTaskAuthStore((s) => s.startPolling)
    const stopPolling = useTaskAuthStore((s) => s.stopPolling)

    const [editingId, setEditingId] = useState<number | null>(null)
    const [editValue, setEditValue] = useState('')
    const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null)
    const editInputRef = useRef<HTMLInputElement>(null)
    const scrollContainerRef = useRef<HTMLDivElement>(null)

    useEffect(() => {
        if (apiKey) {
            fetchSessions(1)
        }
    }, [apiKey, fetchSessions])

    //挂载时开始轮询待授权任务，卸载时停止
    useEffect(() => {
        if (apiKey) {
            startPolling()
        }
        return () => stopPolling()
    }, [apiKey, startPolling, stopPolling])
    // Auto-focus and select text when editing starts
    useEffect(() => {
        if (editingId !== null && editInputRef.current) {
            editInputRef.current.focus()
            editInputRef.current.select()
        }
    }, [editingId])

    const handleNew = useCallback(() => {
        clearCurrentSession()
    }, [clearCurrentSession])

    const handleSelect = useCallback(
        (id: number) => {
            if (editingId !== null) return
            selectBackendSession(id)
        },
        [selectBackendSession, editingId]
    )

    const startEditing = useCallback((id: number, currentTitle: string, e?: React.MouseEvent) => {
        e?.stopPropagation()
        setEditingId(id)
        setEditValue(currentTitle)
    }, [])

    const saveEditing = useCallback(async () => {
        if (editingId === null) return
        const trimmed = editValue.trim()
        if (trimmed && trimmed !== backendSessionList.find(s => s.id === editingId)?.title) {
            await updateBackendSessionTitle(editingId, trimmed)
        }
        setEditingId(null)
        setEditValue('')
    }, [editingId, editValue, backendSessionList, updateBackendSessionTitle])
    const cancelEditing = useCallback(() => {
        setEditingId(null)
        setEditValue('')
    }, [])

    const handleEditKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            e.preventDefault()
            saveEditing()
        } else if (e.key === 'Escape') {
            cancelEditing()
        }
    }, [saveEditing, cancelEditing])
    const handleDeleteConfirm = useCallback(async () => {
        if (deleteConfirmId === null) return
        await deleteBackendSession(deleteConfirmId)
        setDeleteConfirmId(null)
    }, [deleteConfirmId, deleteBackendSession])


    const hasMore = Array.isArray(backendSessionList) && backendSessionList.length < sessionsTotal && sessionsPage < sessionsTotalPages

    // Group backend sessions by time
    const groupedSessions = (() => {
        if (!Array.isArray(backendSessionList)) return [] as [TimeGroup, typeof backendSessionList][]
        const groups: Record<TimeGroup, typeof backendSessionList> = {
            today: [],
            thisWeek: [],
            earlier: []
        }
        for (const s of backendSessionList) {
            const g = getTimeGroup(s.updateTime)
            if (g) groups[g].push(s)
        }
        const result: [TimeGroup, typeof backendSessionList][] = []
        for (const g of ['today', 'thisWeek', 'earlier'] as TimeGroup[]) {
            if (groups[g].length > 0) {
                result.push([g, groups[g]])
            }
        }
        return result
    })()

    const sidebarContent = (
        <div className="flex flex-col h-full">
            {/* === 侧边栏头部：Bot + "Matrix"，无边框 === */}
            <div className="flex items-center justify-between px-2.5 py-2.5">
                <div className="flex items-center gap-2">
                    <div className="w-6 h-6 rounded-full bg-gray-900 dark:bg-gray-100 flex items-center justify-center flex-shrink-0">
                        <Bot className="w-3 h-3 text-white dark:text-gray-900"/>
                    </div>
                    <h2 className="font-semibold text-sm text-gray-800 dark:text-gray-200">Matrix</h2>
                </div>
                <div className="flex items-center gap-1">
                    <ThemeToggle/>
                    <button
                        onClick={toggleSidebar}
                        aria-label="收起侧边栏"
                        className="rounded-lg p-2 hover:bg-gray-200 dark:hover:bg-white/[0.06] transition-colors hidden md:flex"
                    >
                        <PanelLeftClose className="w-4 h-4 text-gray-500"/>
                    </button>
                </div>
            </div>

            {/* === 外链导航按钮行：分段控件风格 === */}
            <div className="px-3 pb-2">
                <div className="flex rounded-lg bg-gray-200/70 dark:bg-white/[0.06] p-0.5">
                    <a
                        href="https://chat.deepseek.com/"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex flex-1 items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs text-gray-500 dark:text-gray-400 hover:bg-white dark:hover:bg-white/[0.15] hover:text-gray-700 dark:hover:text-gray-200 transition-all"
                        title="DeepSeek Chat"
                    >
                        <img src="./deepseek-chat-favicon.svg" alt="Chat" className="w-3.5 h-3.5" />
                        <span>Chat</span>
                    </a>
                    <a
                        href="https://api-docs.deepseek.com/zh-cn/quick_start/pricing/#%E6%89%A3%E8%B4%B9%E8%A7%84%E5%88%99"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex flex-1 items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs text-gray-500 dark:text-gray-400 hover:bg-white dark:hover:bg-white/[0.15] hover:text-gray-700 dark:hover:text-gray-200 transition-all"
                        title="DeepSeek API"
                    >
                        <img src="./deepseek-api-favicon.svg" alt="API" className="w-3.5 h-3.5" />
                        <span>API</span>
                    </a>
                    <a
                        href="https://platform.deepseek.com/usage"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex flex-1 items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs text-gray-500 dark:text-gray-400 hover:bg-white dark:hover:bg-white/[0.15] hover:text-gray-700 dark:hover:text-gray-200 transition-all"
                        title="用量统计"
                    >
                        <img src="./deepseek-platform-favicon.svg" alt="用量统计" className="w-3.5 h-3.5 dark:brightness-0 dark:invert" />
                        <span>统计</span>
                    </a>
                </div>
            </div>

            {/* === "新对话"按钮：实线边框、灰色背景、阴影 === */}
            <div className="px-3 pb-2">
                <button
                    onClick={handleNew}
                    aria-label="新建对话"
                    className="w-full flex items-center justify-center gap-1 rounded-lg border border-solid border-gray-300 dark:border-gray-700 bg-gray-200/60 dark:bg-gray-800/70 shadow-md hover:border-blue-400 dark:hover:border-white/[0.25] hover:bg-blue-50 dark:hover:bg-white/[0.04] px-2 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-gray-200 transition-all"
                >
                    <MessageSquarePlus className="w-4 h-4"/>新对话
                </button>
            </div>

            <div className="flex-1 overflow-y-auto px-1.5 pb-1.5 space-y-0.5" ref={scrollContainerRef} role="list" aria-label="对话列表">
                {!apiKey ? (
                    <div className="text-center py-8 text-gray-700 text-sm">
                        请先设置 API Key
                    </div>
                ) : sessionsLoading && !sessionsLoaded ? (
                    <div className="flex items-center justify-center py-8">
                        <Loader2 className="w-5 h-5 animate-spin text-gray-600"/>
                    </div>
                ) : !Array.isArray(backendSessionList) || (backendSessionList.length === 0 && sessions.filter(s => !s.backendSessionId).length === 0) ? (
                    <div className="text-center py-8 text-gray-700 text-sm">
                        暂无对话
                    </div>
                ) : (
                    <>
                        {/* Local sessions */}
                        {sessions.filter(s => currentSessionId === s.id).map((session) => {
                            const isActive = currentSessionId === session.id
                            return (
                                <div key={session.id} className="relative group">
                                    <div
                                        onClick={() => selectSession(session.id)}
                                        role="button"
                                        tabIndex={0}
                                        aria-current={isActive ? 'true' : undefined}
                                        aria-label={"对话: " + session.title}
                                        className={
                                            'w-full flex flex-col gap-0.5 rounded-lg px-2.5 py-2 text-sm text-left transition-all cursor-pointer ' +
                                            (isActive
                                                ? 'bg-blue-50 dark:bg-white/[0.08] text-blue-600 dark:text-blue-300 font-medium border-l-[3px] border-blue-400 dark:border-blue-500'
                                                : 'text-gray-700 dark:text-gray-300 hover:bg-gray-200/80 dark:hover:bg-white/[0.10]')
                                        }
                                    >
                                        <div className="flex items-center gap-2 w-full">
                                            <MessageSquare className="w-4 h-4 flex-shrink-0 opacity-60"/>
                                            <span className="flex-1 truncate">{session.title}</span>
                                        </div>
                                    </div>
                                </div>
                            )
                        })}

                        {/* Grouped backend sessions */}
                        {groupedSessions.map(([group, sessions]) => (
                            <div key={group}>
                                {/* Group header */}
                                <div className="px-2.5 py-1.5 mt-1 text-xs font-medium text-gray-400 tracking-wider">
                                    {GROUP_LABELS[group]}
                                </div>
                                {sessions.map((session) => {
                                    const isActive = currentSessionId === String(session.id)
                                    const isEditing = editingId === session.id
                                    return (
                                        <SwipeableSessionItem
                                            key={session.id}
                                            session={session}
                                            isActive={isActive}
                                            isEditing={isEditing}
                                            editValue={editValue}
                                            setEditValue={setEditValue}
                                            editInputRef={editInputRef}
                                            onSelect={handleSelect}
                                            onStartEditing={startEditing}
                                            onEditKeyDown={handleEditKeyDown}
                                            onSaveEditing={saveEditing}
                                            onDelete={deleteBackendSession}
                                        />
                                    )
                                })}
                            </div>
                        ))}

                        {hasMore && (
                            <button
                                onClick={loadMoreSessions}
                                disabled={sessionsLoading}
                                className="w-full flex items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-xs text-gray-600 hover:bg-gray-200/80 dark:hover:bg-white/[0.10] transition-colors disabled:opacity-50"
                                aria-label="加载更多"
                            >
                                {sessionsLoading ? (
                                    <Loader2 className="w-3.5 h-3.5 animate-spin"/>
                                ) : (
                                    <ChevronDown className="w-3.5 h-3.5"/>
                                )}
                                {sessionsLoading ? '加载中...' : '加载更多'}
                            </button>
                        )}
                    </>
                )}
            </div>

            {/* === 底部：Matrix 雨开关 + 版本号，无边框 === */}
            <div className="px-3 py-3">
                {/* Matrix Rain 开关 - iOS 风格滑块，仅暗色模式显示 */}
                <label className="hidden dark:flex items-center justify-between px-3 py-2 mb-2 rounded-lg cursor-pointer select-none hover:bg-white/[0.04] transition-colors">
                    <input type="checkbox" className="sr-only" checked={matrixRainEnabled} onChange={toggleMatrixRain} />
                    <span className="flex items-center text-sm font-medium text-gray-400 whitespace-nowrap">
                        Matrix
                        {matrixRainEnabled && isBackendGenerating && (
                            <span className="ml-1.5 w-2 h-2 rounded-full bg-[#00ff41] animate-pulse"/>
                        )}
                    </span>
                    <div className="flex items-center gap-2">
                        <div className={`relative w-11 h-6 rounded-full transition-colors duration-300 ${matrixRainEnabled ? 'bg-[#22c55e]' : 'bg-gray-600'}`}>
                            <div className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow-md transition-transform duration-300 ${matrixRainEnabled ? 'translate-x-5' : 'translate-x-0'}`} />
                        </div>
                    </div>
                </label>

                <p className="text-xs text-gray-400 text-center">
                    Matrix v1.0.2
                </p>
            </div>
        </div>
    )

    return (
        <>
            {/* Desktop sidebar: responsive width + CSS transition */}
            <aside
                className={`
                    hidden md:block overflow-hidden border-r border-gray-200 dark:border-white/[0.06]
                    bg-gray-50 dark:bg-[#1c1c20]
                    transition-[width] duration-250 ease-in-out
                    ${sidebarOpen
                    ? 'w-44 lg:w-48 xl:w-52 2xl:w-56'
                    : 'w-0'
                }
                `}
                aria-label="对话历史侧边栏"
            >
                {sidebarContent}
            </aside>

            <AnimatePresence>
                {sidebarOpen && (
                    <>
                        <motion.div
                            initial={{opacity: 0}}
                            animate={{opacity: 1}}
                            exit={{opacity: 0}}
                            onClick={() => setSidebarOpen(false)}
                            className="md:hidden fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
                            aria-hidden="true"
                        />
                        <motion.aside
                            initial={{x: '-100%'}}
                            animate={{x: 0}}
                            exit={{x: '-100%'}}
                            transition={{type: 'spring', damping: 25, stiffness: 300}}
                            className="md:hidden fixed left-0 top-0 bottom-0 z-50 w-[75vw] max-w-[300px] min-w-[240px] bg-gray-50 dark:bg-[#1c1c20] border-r border-gray-200 dark:border-white/[0.06] shadow-2xl"
                            aria-label="对话历史侧边栏"
                        >
                            {sidebarContent}
                        </motion.aside>
                    </>
                )}
            </AnimatePresence>

            {!sidebarOpen && (
                <button
                    onClick={toggleSidebar}
                    aria-label="展开侧边栏"
                    className="hidden md:flex fixed left-3 top-12 z-30 rounded-lg p-2 bg-white dark:bg-[#1c1c20]/80 dark:backdrop-blur-xl border border-gray-200 dark:border-white/[0.06] shadow-sm hover:bg-gray-100 dark:hover:bg-[#252529] transition-colors"
                >
                    <PanelLeft className="w-4 h-4 text-gray-500"/>
                </button>
            )}

            <AnimatePresence>
                {deleteConfirmId !== null && (
                    <motion.div
                        initial={{opacity: 0}}
                        animate={{opacity: 1}}
                        exit={{opacity: 0}}
                        className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 backdrop-blur-sm"
                    >
                        <motion.div
                            initial={{scale: 0.9, opacity: 0}}
                            animate={{scale: 1, opacity: 1}}
                            exit={{scale: 0.9, opacity: 0}}
                            className="bg-white dark:bg-[#1c1c20] rounded-xl shadow-2xl p-6 max-w-sm w-full mx-4 border border-gray-200 dark:border-white/[0.06]"
                        >
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-2">确认删除</h3>
                            <p className="text-sm text-gray-700 dark:text-gray-300 mb-6">确定要删除这个对话吗？此操作不可撤销。</p>
                            <div className="flex justify-end gap-3">
                                <button
                                    onClick={() => setDeleteConfirmId(null)}
                                    className="px-4 py-2 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/[0.06] transition-colors"
                                >
                                    取消
                                </button>
                                <button
                                    onClick={handleDeleteConfirm}
                                    className="px-4 py-2 rounded-lg text-sm font-medium text-white bg-red-600 hover:bg-red-700 transition-colors"
                                >
                                    删除
                                </button>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>

            <AnimatePresence>
                {modalOpen && (
                    <TaskAuthModal open={true} onClose={closeModal}/>
                )}
            </AnimatePresence>
        </>
    )
}
