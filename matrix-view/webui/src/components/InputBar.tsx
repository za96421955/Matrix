import {useState, useRef, useCallback, useEffect, useMemo} from 'react'
import {Send, Square, Brain} from 'lucide-react'
import {useChatStore} from '../store/chatStore'
import type {BackendSessionSummary} from '../types'

interface InputBarProps {
    onSend: (text: string) => void
    onStop: () => void
    hideTopBorder?: boolean
}

/** 从文本中提取所有 @会话标题 的引用信息 */
function parseReferencedTitles(text: string): string[] {
    const regex = /@([^\s@]+)/g
    const matches: string[] = []
    let match
    while ((match = regex.exec(text)) !== null) {
        if (match[1].trim()) {
            matches.push(match[1].trim())
        }
    }
    return matches
}

export default function InputBar({onSend, onStop, hideTopBorder}: InputBarProps) {
    const [text, setText] = useState('')
    const textareaRef = useRef<HTMLTextAreaElement>(null)
    const lastSendRef = useRef(0)
    const THROTTLE_MS = 1000
    const isStreaming = useChatStore((s) => s.isStreaming)
    const isBackendGenerating = useChatStore((s) => s.isBackendGenerating)
    const thinkingType = useChatStore((s) => s.thinkingType)
    const reasoningEffort = useChatStore((s) => s.reasoningEffort)
    const maxTokens = useChatStore((s) => s.maxTokens)
    const setMaxTokens = useChatStore((s) => s.setMaxTokens)
    const setThinkingType = useChatStore((s) => s.setThinkingType)
    const setReasoningEffort = useChatStore((s) => s.setReasoningEffort)
    const modelType = useChatStore((s) => s.modelType)
    const setModelType = useChatStore((s) => s.setModelType)

    // @ 提及相关状态
    const backendSessionList = useChatStore((s) => s.backendSessionList)
    const currentSessionId = useChatStore((s) => s.currentSessionId)
    const referencedSessions = useChatStore((s) => s.referencedSessions)
    const addReferencedSession = useChatStore((s) => s.addReferencedSession)
    const removeReferencedSession = useChatStore((s) => s.removeReferencedSession)
    
    const [showDropdown, setShowDropdown] = useState(false)
    const [dropdownFilter, setDropdownFilter] = useState('')
    const [selectedIndex, setSelectedIndex] = useState(0)
    const dropdownRef = useRef<HTMLDivElement>(null)
    const lastAtPosRef = useRef(-1)

    // 过滤后的会话列表：排除当前会话
    const filteredSessions = useMemo(() => {
        const currentId = currentSessionId ? Number(currentSessionId) : -1
        const list = backendSessionList.filter((s) => s.id !== currentId && !isNaN(s.id))
        if (!dropdownFilter) return list
        const f = dropdownFilter.toLowerCase()
        return list.filter((s) => s.title.toLowerCase().includes(f))
    }, [backendSessionList, currentSessionId, dropdownFilter])

    // 同步文本中的 @引用 到 store
    useEffect(() => {
        const titlesInText = parseReferencedTitles(text)
        // 找到当前 backendSessionList 中匹配的会话
        const matched = backendSessionList.filter((s) =>
            titlesInText.includes(s.title)
        )
        const matchedIds = new Set(matched.map((s) => s.id))
        // 添加新的引用
        for (const session of matched) {
            if (!referencedSessions.find((rs) => rs.id === session.id)) {
                addReferencedSession(session)
            }
        }
        // 移除文本中不再出现的引用
        for (const rs of referencedSessions) {
            if (!matchedIds.has(rs.id)) {
                removeReferencedSession(rs.id)
            }
        }
    }, [text, backendSessionList, referencedSessions, addReferencedSession, removeReferencedSession])

    // 点击外部关闭下拉
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setShowDropdown(false)
            }
        }
        document.addEventListener('mousedown', handleClickOutside)
        return () => document.removeEventListener('mousedown', handleClickOutside)
    }, [])

    // Auto-resize textarea
    useEffect(() => {
        const ta = textareaRef.current
        if (ta) {
            ta.style.height = 'auto'
            ta.style.height = Math.min(Math.max(ta.scrollHeight, 40), 200) + 'px'
        }
    }, [text])

    // 处理输入变化 - 检测 @ 触发下拉
    const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const value = e.target.value
        setText(value)

        // 检测最后一个 @ 符号
        const lastAtIndex = value.lastIndexOf('@')
        if (lastAtIndex >= 0) {
            // 检查 @ 后面是否有空格（有空格则不触发）
            const afterAt = value.slice(lastAtIndex + 1)
            // 如果 @ 后面有空格，关闭下拉
            if (afterAt.includes(' ')) {
                setShowDropdown(false)
                return
            }
            // 提取 @ 后的文本用于过滤
            const filterText = afterAt
            setDropdownFilter(filterText)
            lastAtPosRef.current = lastAtIndex
            setShowDropdown(true)
            setSelectedIndex(0)
        } else {
            setShowDropdown(false)
        }
    }, [])

    // 选中一个会话
    const selectSession = useCallback((session: BackendSessionSummary) => {
        const ta = textareaRef.current
        if (!ta) return

        const pos = lastAtPosRef.current
        if (pos < 0) return

        // 替换 @ 及之后的文本为 @会话标题
        const beforeAt = text.slice(0, pos)
        // 找到 @ 后到当前光标或结尾的文本
        const afterAt = text.slice(pos)
        // 计算需要替换的长度（从 @ 到下一个空格或结尾）
        const spaceIdx = afterAt.indexOf(' ')
        const replaceLen = spaceIdx >= 0 ? spaceIdx : afterAt.length
        const afterReplace = text.slice(pos + replaceLen)

        const newText = beforeAt + '@' + session.title + ' ' + afterReplace
        setText(newText)
        setShowDropdown(false)
        addReferencedSession(session)

        // 聚焦 textarea 并定位光标到末尾
        ta.focus()
        const newCursorPos = beforeAt.length + session.title.length + 2
        setTimeout(() => {
            ta.setSelectionRange(newCursorPos, newCursorPos)
        }, 0)
    }, [text, addReferencedSession])

    const handleSend = useCallback(() => {
        const now = Date.now()
        if (now - lastSendRef.current < THROTTLE_MS) return
        lastSendRef.current = now
        const trimmed = text.trim()
        if (!trimmed || isStreaming || isBackendGenerating) return
        setText('')
        onSend(trimmed)
        if (textareaRef.current) {
            textareaRef.current.style.height = 'auto'
            textareaRef.current.style.minHeight = '40px'
            textareaRef.current.focus()
        }
    }, [text, isStreaming, onSend])

    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (showDropdown) {
            if (e.key === 'ArrowDown') {
                e.preventDefault()
                setSelectedIndex((prev) => (prev + 1) % Math.max(filteredSessions.length, 1))
                return
            }
            if (e.key === 'ArrowUp') {
                e.preventDefault()
                setSelectedIndex((prev) => (prev - 1 + filteredSessions.length) % Math.max(filteredSessions.length, 1))
                return
            }
            if (e.key === 'Enter' && filteredSessions.length > 0) {
                e.preventDefault()
                selectSession(filteredSessions[selectedIndex])
                return
            }
            if (e.key === 'Escape') {
                e.preventDefault()
                setShowDropdown(false)
                return
            }
        }

        if ((e.metaKey || e.altKey || e.shiftKey || e.ctrlKey) && e.key === 'Enter') {
            e.preventDefault()
            handleSend()
        } else if (e.key === 'Escape') {
            ;(e.target as HTMLTextAreaElement).blur()
        }
    }, [showDropdown, filteredSessions, selectedIndex, selectSession, handleSend])

    // 引用会话 badges
    const referencedBadges = useMemo(() => {
        const titlesInText = parseReferencedTitles(text)
        return referencedSessions.filter((rs) => titlesInText.includes(rs.title))
    }, [referencedSessions, text])

    return (
        <div className={`${hideTopBorder ? '' : 'border-t border-gray-200 dark:border-white/[0.04]'} bg-white dark:bg-[#18181B]`}>
            <div className="max-w-4xl lg:max-w-5xl xl:max-w-6xl2xl:max-w-7xl mx-auto px-3 sm:px-4 py-1.5 sm:py-2">
                <div
                    className="relative rounded-2xl border border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-[#1c1c20] shadow-sm transition-colors focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-500/20 dark:focus-within:border-blue-500">
                    {/* @引用会话下拉菜单 */}
                    {showDropdown && filteredSessions.length > 0 && (
                        <div
                            ref={dropdownRef}
                            className="absolute bottom-full left-0 right-0 mb-1 z-50 rounded-lg border border-gray-200 dark:border-white/[0.08] bg-white dark:bg-[#1c1c20]/95 dark:backdrop-blur-2xl shadow-2xl py-1 max-h-[200px] overflow-y-auto"
                        >
                            {filteredSessions.map((session, idx) => (
                                <div
                                    key={session.id}
                                    onClick={() => selectSession(session)}
                                    className={
                                        'flex items-center justify-between px-3 py-2 text-sm cursor-pointer transition-colors ' +
                                        (idx === selectedIndex
                                            ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                            : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/[0.04]')
                                    }
                                >
                                    <span className="overflow-hidden text-ellipsis whitespace-nowrap flex-1 mr-2">
                                        @{session.title}
                                    </span>
                                    <span className="text-[10px] text-gray-400 dark:text-gray-500 flex-shrink-0">
                                        #{session.id}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}

                    <textarea
                        ref={textareaRef}
                        id="chat-input"
                        rows={1}
                        value={text}
                        onChange={handleChange}
                        onKeyDown={handleKeyDown}
                        placeholder="输入消息，Enter换行，Ctrl+Enter发送。使用 @ 引用其他会话"
                        disabled={isStreaming || isBackendGenerating}
                        className="w-full resize-none bg-transparent px-4 pt-3 pb-1 text-sm placeholder:text-gray-500 dark:placeholder:text-gray-400 outline-none disabled:opacity-50 disabled:cursor-not-allowed"
                        style={{minHeight: '40px', maxHeight: '200px'}}
                        aria-disabled={isStreaming || isBackendGenerating}
                    />

                    {/* 引用会话 badges */}
                    {referencedBadges.length > 0 && (
                        <div className="flex items-center gap-1.5 px-3 pb-0 flex-wrap">
                            {referencedBadges.map((session) => (
                                <span
                                    key={session.id}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-medium bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-800"
                                >
                                    引用: @{session.title.length > 20 ? session.title.slice(0, 20) + '...' : session.title}
                                </span>
                            ))}
                        </div>
                    )}

                    <div className="flex items-center justify-between px-3 pb-2">
                        <div className="flex items-center gap-2 flex-wrap">
                            {/* flash/pro 模型选择 */}
                            <div
                                className="flex items-center rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                                {(['flash', 'pro'] as const).map((type) => (
                                    <button
                                        key={type}
                                        onClick={() => setModelType(type)}
                                        className={
                                            'px-2 py-1.5 text-xs font-medium transition-colors ' +
                                            (modelType === type
                                                ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                                : 'bg-transparent text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/[0.04]')
                                        }
                                        aria-label={'模型: ' + type}
                                    >
                                        {type === 'flash' ? 'flash' : 'pro'}
                                    </button>
                                ))}
                            </div>

                            {/*深度思考按钮 */}
                            <button
                                onClick={() => setThinkingType(thinkingType === 'enabled' ? 'disabled' : 'enabled')}
                                className={
                                    'flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ' +
                                    (thinkingType === 'enabled'
                                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                        : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/[0.04]')
                                }
                                aria-label={thinkingType === 'enabled' ? '关闭深度思考' : '开启深度思考'}
                            >
                                <Brain className="w-3.5 h-3.5"/>
                                <span className="hidden sm:inline">深度思考</span>
                            </button>

                            {/*思考深度选择 (一般/深度) */}
                            <div
                                className="flex items-center rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
                                {(['high', 'max'] as const).map((level) => (
                                    <button
                                        key={level}
                                        onClick={() => setReasoningEffort(level)}
                                        className={
                                            'px-2 py-1.5 text-xs font-medium transition-colors ' +
                                            (reasoningEffort === level
                                                ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                                : 'bg-transparent text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/[0.04]')
                                        }
                                        aria-label={'思考深度: ' + level}
                                    >
                                        {level === 'high' ? '一般' : '深度'}
                                    </button>
                                ))}
                            </div>

                            {/*输出长度选择 (4096~32768) */}
                            <select
                                value={maxTokens}
                                onChange={(e) => setMaxTokens(Number(e.target.value))}
                                className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#18181B] px-1.5 sm:px-2 py-1 text-[10px] sm:text-xs font-medium text-gray-700 dark:text-gray-200 outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all cursor-pointer"
                                aria-label="输出长度"
                            >
                                <option value={4096}>4096</option>
                                <option value={8192}>8192</option>
                                <option value={16384}>16384</option>
                                <option value={32768}>32768</option>
                            </select>
                        </div>

                        {/*发送/停止按钮 */}
                        <div className="flex-shrink-0">
                            {(isStreaming || isBackendGenerating) ? (
                                <button
                                    onClick={onStop}
                                    aria-label="停止生成"
                                    className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/50"
                                >
                                    <Square className="w-3.5 h-3.5"/>
                                    <span className="hidden sm:inline">停止</span>
                                </button>
                            ) : (
                                <button
                                    onClick={handleSend}
                                    disabled={!text.trim()}
                                    aria-label="发送消息"
                                    className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-900/50 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <Send className="w-3.5 h-3.5"/>
                                    <span className="hidden sm:inline">发送</span>
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
