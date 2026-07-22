import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { User, Brain, X, Copy, Check, ChevronRight, Wrench, Lightbulb, Info, Cloud, MapPin, Clock, Timer, Code2, Puzzle, Settings, Terminal, QrCode, FileText, AlertCircle} from 'lucide-react'
import MarkdownRenderer from './MarkdownRenderer'
import { useToastStore } from '../store/toastStore'
import type { Message, ToolCall } from '../types'
import { useChatStore } from '../store/chatStore'
import MatrixLogo from "./MatrixLogo";

interface MessageBubbleProps {
    message: Message
    isStreaming?: boolean
    onDelete?: () => void
    toolResultsMap?: Record<string, string>
    isToolCallExpanded?: boolean
    onToggleToolCall?: () => void
}

async function copyToClipboard(text: string): Promise<boolean> {
    try {
        await navigator.clipboard.writeText(text)
        return true
    } catch {
        try {
            const ta = document.createElement('textarea')
            ta.value = text
            ta.style.position = 'fixed'
            ta.style.opacity = '0'
            document.body.appendChild(ta)
            ta.select()
            const ok = document.execCommand('copy')
            document.body.removeChild(ta)
            return ok
        } catch {
            return false
        }
    }
}

function formatArguments(args: string): string {
    try {
        const parsed = JSON.parse(args)
        return JSON.stringify(parsed, null, 2)
    } catch {
        return args
    }
}



// ===================== getToolIcon: 根据工具名称返回对应图标 =====================
const getToolIcon = (name: string) => {
    const iconMap: Record<string, React.ComponentType<any>> = {
        'weather': Cloud,
        'location': MapPin,
        'time': Clock,
        'timer': Timer,
        'memory': Brain,
        'github': Code2,
        'skill': Puzzle,
        'skill-manager': Settings,
        'terminal': Terminal, 'cli': Terminal,
        'qrcode': QrCode,
        'assistant': FileText, 'file': FileText,
    }
    const lowerName = name.toLowerCase()
    if (iconMap[lowerName]) return iconMap[lowerName]
    for (const [key, icon] of Object.entries(iconMap)) {
        if (lowerName.includes(key)) return icon
    }
    return Wrench
}

// ===================== ToolCallSection 组件 =====================
// 统一的工具调用折叠区。展开后：思考过程 -> 回答内容 -> 逐个工具(参数+结果)
const ToolCallSection = React.memo(({
    toolCalls,
    toolResultsMap,
    reasoningContent,
    content,
    isStreaming,
    markdownEnabled,
    expanded,
    onToggle,
}: {
    toolCalls: ToolCall[]
    toolResultsMap?: Record<string, string>
    reasoningContent?: string
    content?: string
    isStreaming?: boolean
    markdownEnabled?: boolean
    expanded?: boolean
    onToggle?: () => void
}) => {
    const FirstIcon = useMemo(() => {
        const firstName = toolCalls[0]?.function?.name || ''
        return getToolIcon(firstName)
    }, [toolCalls])
    const sectionRef = useRef<HTMLDivElement>(null)
    // 用 ref 保存 onToggle，避免 effect 因 onToggle 引用变化而频繁重订阅
    const onToggleRef = useRef(onToggle)
    onToggleRef.current = onToggle

    useEffect(() => {
        if (expanded && sectionRef.current) {
            const timer = setTimeout(() => {
                sectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
            }, 350)
            return () => clearTimeout(timer)
        }
    }, [expanded])


    // 展开时点击外部自动收起
    useEffect(() => {
        if (!expanded) return

        const handleClickOutside = (e: MouseEvent) => {
            if (sectionRef.current && !sectionRef.current.contains(e.target as Node)) {
                onToggleRef.current?.()
            }
        }

        document.addEventListener("mousedown", handleClickOutside)
        return () => document.removeEventListener("mousedown", handleClickOutside)
    }, [expanded])
    // 渲染内容（QRCODE / Markdown / 纯文本）
    const renderContent = useCallback((text: string) => {
        const qrRegex = /\[QRCODE\]\n([\s\S]*?)\n\[\/QRCODE\]/
        const qrMatch = text.match(qrRegex)
        if (qrMatch) {
            const parts = text.split(qrRegex)
            const before = parts[0] || ''
            const qrContent = parts[1] || ''
            const after = parts[2] || ''
            return (
                <>
                    {before && (isStreaming || !markdownEnabled ? (
                        <p className="whitespace-pre-wrap break-words">{before}</p>
                    ) : (
                        <MarkdownRenderer content={before} />
                    ))}
                    {qrContent && (
                        <pre className="qrcode overflow-x-auto">{qrContent}</pre>
                    )}
                    {after && (isStreaming || !markdownEnabled ? (
                        <p className="whitespace-pre-wrap break-words">{after}</p>
                    ) : (
                        <MarkdownRenderer content={after} />
                    ))}
                </>
            )
        }
        return isStreaming || !markdownEnabled ? (
            <p className="whitespace-pre-wrap break-words">{text}</p>
        ) : (
            <MarkdownRenderer content={text} />
        )
    }, [isStreaming, markdownEnabled])

    const hasReasoning = !!reasoningContent
    const hasContent = !!content?.trim()

    return (
        <div ref={sectionRef} className="rounded-xl border border-gray-200/50 dark:border-white/[0.06]/40 bg-white/80 dark:bg-[#1c1c20]/80 backdrop-blur-sm shadow-sm overflow-hidden">
            {/* 折叠态按钮 */}
            <button
                onClick={onToggle}
                className="w-full flex items-center gap-2 px-3 py-2.5 text-left transition-colors hover:bg-black/5 dark:hover:bg-white/5"
                aria-label={expanded ? '折叠工具调用详情' : '展开工具调用详情'}
            >
                <FirstIcon className="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0" />
                <div className="flex items-baseline gap-1.5 min-w-0 flex-1">
                    <span className="text-sm font-bold text-gray-900 dark:text-gray-100 whitespace-nowrap">
                        {toolCalls[0]?.function?.name || 'ToolCall'}
                    </span>
                    {toolCalls.length > 1 ? (
                        <span className="text-xs text-gray-600 dark:text-gray-300 whitespace-nowrap">
                            等{toolCalls.length}个工具
                        </span>
                    ) : (
                        <span className="text-xs text-gray-600 dark:text-gray-300 truncate">
                            {(() => {
                                try {
                                    const args = JSON.parse(toolCalls[0]?.function?.arguments || '{}')
                                    const funcName = toolCalls[0]?.function?.name || ''
                                    // cli-executor / terminal 优先展示 commands[0]
                                    if ((funcName === 'cli-executor' || funcName === 'terminal') && Array.isArray(args.commands) && args.commands.length > 0) {
                                        const cmd = args.commands[0]
                                        const preview = typeof cmd === 'string' ? cmd : JSON.stringify(cmd)
                                        return '：' + (preview.length > 60 ? preview.substring(0, 57) + '...' : preview)
                                    }
                                    const keys = Object.keys(args)
                                    if (keys.length > 0) {
                                        // 优先展示非 clientId 的其他参数，若只有 clientId 则展示它
                                        const nonClientKeys = keys.filter(k => k !== 'clientId')
                                        const targetKey = nonClientKeys.length > 0 ? nonClientKeys[0] : keys[0]
                                        let val = args[targetKey]
                                        if (typeof val === 'string') val = '"' + val + '"'
                                        else val = JSON.stringify(val)
                                        const preview = targetKey + ': ' + val
                                        return '：' + (preview.length > 60 ? preview.substring(0, 57) + '...' : preview)
                                    }
                                } catch {}
                                const raw = toolCalls[0]?.function?.arguments || ''
                                return '：' + (raw.length > 60 ? raw.substring(0, 57) + '...' : raw)
                            })()}
                        </span>
                    )}
                </div>
                <motion.span
                    animate={{ rotate: expanded ? 90 : 0 }}
                    transition={{ duration: 0.2 }}
                    className="flex-shrink-0"
                >
                    <ChevronRight className="w-4 h-4 text-gray-500" />
                </motion.span>
            </button>

            {/* 展开态内容 */}
            <AnimatePresence>
                {expanded && (
                    <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.2 }}
                        className="border-t border-gray-200/50 dark:border-white/[0.06]/30"
                    >
                        <div className="p-3 space-y-1">
                            {/* 1. 思考过程区块 */}
                            {hasReasoning && (
                                <div className="bg-amber-50 dark:bg-amber-900/10 rounded-lg p-3 border-l-4 border-l-amber-400 dark:border-l-amber-500">
                                    <div className="flex items-center gap-1.5 mb-1.5">
                                        <Brain className="w-3.5 h-3.5 text-amber-500" />
                                        <span className="text-xs font-medium text-amber-700 dark:text-amber-400">
                                            {isStreaming ? '思考中...' : '思考过程'}
                                        </span>
                                    </div>
                                    <div className="text-xs text-amber-800 dark:text-amber-300 leading-relaxed whitespace-pre-wrap break-words">
                                        {reasoningContent}
                                    </div>
                                </div>
                            )}

                            {/* 2. 回答内容区块 */}
                            {hasContent && (
                                <div className="rounded-lg p-3 bg-gray-50/70 dark:bg-[#252529]/40 text-sm leading-relaxed break-words text-gray-900 dark:text-gray-100">
                                    {renderContent(content || '')}
                                </div>
                            )}

                            {/* 3. 工具调用列表 - 每个工具独立子卡片 */}
                            {toolCalls.map((tc, i) => {
                                const ToolIcon = getToolIcon(tc.function?.name || '')
                                const formattedArgs = formatArguments(tc.function?.arguments ?? '')
                                const resultContent = toolResultsMap?.[tc.id]
                                const isQRCode = !!resultContent?.match(/^\[QRCODE\]\n[\s\S]*?\n\[\/QRCODE\]$/)
                                return (
                                    <div
                                        key={tc.id || i}
                                        className="border border-gray-200/40 dark:border-white/[0.06]/30 rounded-lg p-3 bg-white/60 dark:bg-[#1c1c20]/60 backdrop-blur-[2px]"
                                    >
                                        {/* 工具标题行 */}
                                        <div className="flex items-center gap-2 mb-2.5 pb-2 border-b border-gray-100/50 dark:border-white/[0.06]/20">
                                            <ToolIcon className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                                            <span className="text-sm font-bold text-gray-900 dark:text-gray-100 tracking-tight">
                                                {tc.function?.name || 'Unknown'}
                                            </span>
                                        </div>

                                        {/* 参数 */}
                                        {formattedArgs && formattedArgs !== '{}' && (
                                            <div className="mb-2.5">
                                                <span className="text-[10px] font-medium text-gray-600 dark:text-gray-300 uppercase tracking-wide mb-1 block">参数</span>
                                                <pre className="text-xs bg-gray-50/70 dark:bg-[#252529]/40 rounded-lg p-2.5 overflow-x-auto font-mono text-gray-800 dark:text-gray-200 leading-relaxed border border-gray-100/40 dark:border-white/[0.06]/20">
                                                    {formattedArgs}
                                                </pre>
                                            </div>
                                        )}

                                        {/* 结果 */}
                                        <div>
                                            <span className="text-[10px] font-medium text-gray-600 dark:text-gray-300 uppercase tracking-wide mb-1 block">结果</span>
                                            {resultContent ? (
                                                isQRCode ? (
                                                    <pre className="qrcode overflow-x-auto text-xs text-gray-800 dark:text-gray-200">
                                                        {resultContent.replace(/^\[QRCODE\]\n([\s\S]*?)\n\[\/QRCODE\]$/, '$1')}
                                                    </pre>
                                                ) : (
                                                    <div className="text-xs text-gray-800 dark:text-gray-200 leading-relaxed">
                                                        {markdownEnabled ? (
                                                            <MarkdownRenderer content={resultContent} />
                                                        ) : (
                                                            <p className="whitespace-pre-wrap break-words">{resultContent}</p>
                                                        )}
                                                    </div>
                                                )
                                            ) : (
                                                <span className="text-xs text-gray-500 italic">
                                                    {isStreaming ? '等待返回...' : '无返回结果'}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                )
                            })}
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    )
})
function ReasoningBlock({ content, defaultOpen, title }: { content: string; defaultOpen?: boolean; title?: string }) {
    const [open, setOpen] = useState(defaultOpen !== false)
    return (
        <div className="mb-1.5">
            <button
                onClick={() => setOpen(!open)}
                className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
            >
                <Brain className="w-3.5 h-3.5" />
                <span>{title || '思考过程'}</span>
                <motion.span animate={{ rotate: open ? 180 : 0 }} transition={{ duration: 0.2 }} className="inline-block">▼</motion.span>
            </button>
            {open && (
                <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: 'auto', opacity: 1 }}
                    className="mt-1 p-2 rounded-lg bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-white/[0.06] text-xs text-gray-600 dark:text-gray-300 leading-relaxed whitespace-pre-wrap overflow-auto max-h-40 break-words"
                >
                    {content}
                </motion.div>
            )}
        </div>
    )
}

function BubbleActions({ showCopy, showDelete, onCopy, onDelete, copied }: {
    showCopy: boolean
    showDelete: boolean
    onCopy?: () => void
    onDelete?: () => void
    copied?: boolean
}) {
    const [showConfirm, setShowConfirm] = useState(false)

    const handleDeleteClick = useCallback((e: React.MouseEvent) => {
        e.stopPropagation()
        setShowConfirm(true)
    }, [])

    const handleConfirm = useCallback(() => {
        setShowConfirm(false)
        onDelete?.()
    }, [onDelete])

    const handleCancel = useCallback(() => {
        setShowConfirm(false)
    }, [])

    if (!showCopy && !showDelete) return null

    return (
        <>
            <div className="absolute -top-2.5 right-0 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all z-10">
                {showCopy && onCopy && (
                    <button
                        onClick={(e) => { e.stopPropagation(); onCopy() }}
                        aria-label={copied ? '已复制' : '复制'}
                        className="rounded-full p-1.5 bg-white dark:bg-gray-800 shadow-md border border-gray-200 dark:border-white/[0.06] hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 transition-all"
                    >
                        {copied ? <Check className="w-3.5 h-3.5 text-green-500" /> : <Copy className="w-3.5 h-3.5" />}
                    </button>
                )}
                {showDelete && onDelete && (
                    <button
                        onClick={handleDeleteClick}
                        aria-label="删除消息"
                        className="rounded-full p-1.5 bg-white dark:bg-gray-800 shadow-md border border-gray-200 dark:border-white/[0.06] hover:bg-red-50 dark:hover:bg-red-900/30 text-gray-400 hover:text-red-500 transition-all"
                    >
                        <X className="w-3.5 h-3.5" />
                    </button>
                )}
            </div>
            <AnimatePresence>
                {showConfirm && (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 backdrop-blur-sm"
                        onClick={handleCancel}
                    >
                        <motion.div
                            initial={{ scale: 0.9, opacity: 0 }}
                            animate={{ scale: 1, opacity: 1 }}
                            exit={{ scale: 0.9, opacity: 0 }}
                            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
                            className="bg-white dark:bg-[#1c1c20] rounded-xl border border-gray-200 dark:border-gray-800 shadow-2xl p-6 max-w-sm w-full mx-4"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200 mb-2">确认删除</h3>
                            <p className="text-sm text-gray-600 dark:text-gray-300 mb-6">确定要删除该消息吗？此操作不可撤销。</p>
                            <div className="flex justify-end gap-3">
                                <button
                                    onClick={handleCancel}
                                    className="px-4 py-2 text-sm font-medium rounded-lg border border-gray-200 dark:border-white/[0.06] text-gray-800 dark:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                                >
                                    取消
                                </button>
                                <button
                                    onClick={handleConfirm}
                                    className="px-4 py-2 text-sm font-medium rounded-lg bg-red-600 hover:bg-red-700 text-white transition-colors"
                                >
                                    删除
                                </button>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </>
    )
}

function MessageBubble({ message, isStreaming, onDelete, toolResultsMap, isToolCallExpanded, onToggleToolCall }: MessageBubbleProps) {
    const isUser = message.role === 'user'
    const isTool = message.role === 'tool'
    const isAssistant = message.role === 'assistant'
    const isSystem = message.role === 'system'

    const [copied, setCopied] = useState(false)
    const [errorExpanded, setErrorExpanded] = useState(false)
    const markdownEnabled = useChatStore((s) => s.markdownEnabled)

    const showToast = useCallback((msg: string) => {
        useToastStore.getState().addToast({type: 'success', message: msg})
    }, [])

    const handleCopyText = useCallback(async (text: string) => {
        const success = await copyToClipboard(text)
        if (success) {
            showToast('已复制到剪贴板')
            setCopied(true)
            setTimeout(() => setCopied(false), 1500)
        }
    }, [showToast])

    // ===== System message =====
    if (isSystem) {
        if (!message.content || !message.content.trim()) return null
        return (
            <motion.div initial={{opacity: 0, y: 8}} animate={{opacity: 1, y: 0}}
                        className="flex justify-center px-4 py-1">
                <div
                    className="max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[70%] lg:max-w-[60%] xl:max-w-[70%] 2xl:max-w-[65%] w-full rounded-lg px-3 py-2 bg-gray-50/50 dark:bg-[#1c1c20]/50 border border-gray-200 dark:border-gray-800">
                    <div className="flex items-center gap-1.5 mb-1">
                        <Info className="w-3 h-3 text-gray-500"/>
                        <span className="text-xs text-gray-500 dark:text-gray-400 font-medium">系统提示</span>
                    </div>
                    <p className="text-xs text-gray-600 dark:text-gray-300 leading-relaxed break-words">{message.content}</p>
                </div>
            </motion.div>
        )
    }

    // ===== Tool message (降级兼容：未被 assistant 的 toolCalls 消费时独立展示) =====
    if (isTool) {
        return (
            <motion.div initial={{opacity: 0, y: 8}} animate={{opacity: 1, y: 0}}
                        className="flex items-start gap-3 px-4 py-2 group">
                {/* 不可见占位，与 assistant toolcall 分支的 Bot 头像对齐 */}
                <div className="flex-shrink-0 w-8 h-8" aria-hidden="true" />
                <div className="flex-1 min-w-0 max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[75%] lg:max-w-[65%] xl:max-w-[70%] 2xl:max-w-[65%] break-words relative">
                    <BubbleActions
                        showCopy={!!(message.content && message.content.trim())}
                        showDelete={!!onDelete}
                        onCopy={() => handleCopyText(message.content || '')}
                        onDelete={onDelete}
                        copied={copied}
                    />
                    <ToolCallSection
                        toolCalls={[{ id: message.toolCallId || '', type: 'function' as const, function: { name: 'ToolCall', arguments: '' } }]}
                        isStreaming={isStreaming}
                        markdownEnabled={markdownEnabled}
                        expanded={isToolCallExpanded}
                        onToggle={onToggleToolCall}
                    />
                </div>
            </motion.div>
        )
    }

    // ===== User message =====
    if (isUser) {
        if (!message.content || !message.content.trim()) return null
        return (
            <motion.div
                initial={{opacity: 0, y: 12}}
                animate={{opacity: 1, y: 0}}
                transition={{duration: 0.3, ease: 'easeOut'}}
                className="flex items-start gap-3 px-4 py-2 group flex-row-reverse"
                role="listitem"
                aria-label="用户消息"
            >
                <div
                    className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shadow-sm bg-gray-300 dark:bg-gray-600"
                    aria-hidden="true">
                    <User className="w-4 h-4 text-gray-800 dark:text-gray-100"/>
                </div>
                <div
                    className="flex-1 min-w-0 max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[75%] lg:max-w-[65%] xl:max-w-[70%] 2xl:max-w-[65%] flex items-end flex-col">
                    <div className="max-w-full break-words">
                        <div
                            className="rounded-2xl px-4 py-3 shadow-sm max-w-full relative ml-auto bg-gradient-to-br from-[#2563eb] to-[#3b82f6] text-white rounded-tr-sm shadow-md shadow-blue-500/20">
                            <p className="whitespace-pre-wrap text-sm leading-relaxed break-words">{message.content}</p>
                            <BubbleActions
                                showCopy={true}
                                showDelete={!!onDelete}
                                onCopy={() => handleCopyText(message.content || '')}
                                onDelete={onDelete}
                                copied={copied}
                            />
                        </div>
                    </div>
                </div>
            </motion.div>
        )
    }

    // ========================================================================
    // ========================================================================
    //  Assistant message -- 重构后保留两个分支
    // ========================================================================
    if (isAssistant) {
        const hasReasoning = !!message.reasoningContent
        const hasToolCalls = !!(message.toolCalls && message.toolCalls.length > 0)
        const hasContent = !!message.content?.trim()
        const isEmpty = !hasReasoning && !hasToolCalls && !hasContent && !isStreaming

        if (isEmpty) return null

        // 安全的 toolCalls 列表：过滤掉 function.name 为空的项
        const safeToolCalls = (message.toolCalls || []).filter(tc => tc?.function?.name)

        const toolCallsText = safeToolCalls
            .map(tc => `工具: ${tc.function.name}\n参数: ${formatArguments(tc.function.arguments)}`)
            .join('\n\n')

        // ====================================================================
        // 分支2：含有工具调用 -- 有占位div对齐，内容收进ToolCallSection
        // ====================================================================
        if (hasToolCalls && safeToolCalls.length > 0) {
            const copyTargetText = hasContent
                ? (message.content || '')
                : toolCallsText

            return (
                <motion.div
                    initial={{opacity: 0, y: 12}}
                    animate={{opacity: 1, y: 0}}
                    transition={{duration: 0.3, ease: 'easeOut'}}
                    className="flex items-start gap-3 px-4 py-2 group"
                    role="listitem"
                    aria-label="AI工具调用"
                >
                    {/* 不可见占位，与分支1的 Bot 头像对齐 */}
                    <div className="flex-shrink-0 w-8 h-8" aria-hidden="true" />
                    <div className="flex-1 min-w-0 max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[75%] lg:max-w-[65%] xl:max-w-[70%] 2xl:max-w-[65%] break-words">
                        <div className="relative">
                            <BubbleActions
                                showCopy={!!copyTargetText}
                                showDelete={!!onDelete}
                                onCopy={() => handleCopyText(copyTargetText)}
                                onDelete={onDelete}
                                copied={copied}
                            />
                            <ToolCallSection
                                toolCalls={safeToolCalls}
                                toolResultsMap={toolResultsMap}
                                reasoningContent={hasReasoning ? message.reasoningContent : undefined}
                                content={hasContent ? message.content : undefined}
                                isStreaming={isStreaming}
                                markdownEnabled={markdownEnabled}
                                expanded={isToolCallExpanded}
                                onToggle={onToggleToolCall}
                            />
                        </div>
                    </div>
                </motion.div>
            )
        }
        // 分支1：无工具调用 -- 有 Bot 头像，常规样式
        // 合并纯推理(hasReasoning && !hasContent)、纯文本、推理+文本
        // ====================================================================
        return (
            <motion.div
                initial={{opacity: 0, y: 12}}
                animate={{opacity: 1, y: 0}}
                transition={{duration: 0.3, ease: 'easeOut'}}
                className="flex items-start gap-3 px-4 py-2 group"
                role="listitem"
                aria-label={hasReasoning ? "AI思考中" : "AI消息"}
            >
                <div
                    className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shadow-sm bg-black dark:bg-white"
                    aria-hidden="true">
                    <MatrixLogo size="sm" className="w-4 h-4 text-gray-700 dark:text-gray-200"/>
                </div>
                <div
                    className="flex-1 min-w-0 max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[75%] lg:max-w-[65%] xl:max-w-[70%] 2xl:max-w-[65%] break-words">
                    {(() => {
                        // 纯推理（无内容）：特殊卡片样式
                        if (hasReasoning && !hasContent) {
                            return (
                                <div
                                    className="rounded-lg border-l-4 border-l-yellow-400 dark:border-l-yellow-500 bg-gray-50 dark:bg-[#1c1c20]/60 border border-gray-200 dark:border-white/[0.06] p-3">
                                    <div className="flex items-center gap-2 mb-2">
                                        <Lightbulb className="w-4 h-4 text-yellow-500"/>
                                        <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                                            {isStreaming ? '思考中...' : '思考过程'}
                                        </span>
                                        {isStreaming && (
                                            <span className="flex items-center gap-1 text-xs text-gray-500 ml-auto">
                                                <span className="w-1.5 h-1.5 rounded-full bg-yellow-500 animate-pulse"/>
                                                思考中
                                            </span>
                                        )}
                                    </div>
                                    <div
                                        className="text-xs text-gray-600 dark:text-gray-300 leading-relaxed whitespace-pre-wrap break-words">
                                        {message.reasoningContent}
                                    </div>
                                </div>
                            );
                        }

                        // 有内容的展示（可能是纯文本或推理+文本）
                        return (
                            <>
                                {hasReasoning && (
                                    <ReasoningBlock
                                        content={message.reasoningContent!}
                                        defaultOpen={!isStreaming}
                                        title={isStreaming ? '思考中...' : '思考过程'}
                                    />
                                )}
                                {hasContent && (
                                    <div className="relative">
                                        <div
                                            className={
                                                'rounded-2xl px-4 py-3 shadow-sm max-w-full relative bg-gray-100 dark:bg-[#26262c] text-gray-900 dark:text-gray-100 rounded-tl-sm' +
                                                (isStreaming ? ' border-2 border-blue-300 dark:border-blue-500/60' : '')
                                            }
                                        >
                                            <div className="text-sm leading-relaxed break-words">
                                                {(() => {
                                                    const qrRegex = /\[QRCODE\]\n([\s\S]*?)\n\[\/QRCODE\]/;
                                                    const qrMatch = message.content?.match(qrRegex);
                                                    if (qrMatch) {
                                                        const parts = (message.content || '').split(qrRegex);
                                                        const before = parts[0] || '';
                                                        const qrContent = parts[1] || '';
                                                        const after = parts[2] || '';
                                                        return (
                                                            <>
                                                                {before && (isStreaming || !markdownEnabled ? (
                                                                    <p className="whitespace-pre-wrap break-words">{before}</p>
                                                                ) : (
                                                                    <MarkdownRenderer content={before}/>
                                                                ))}
                                                                {qrContent && (
                                                                    <pre className="qrcode overflow-x-auto">{qrContent}</pre>
                                                                )}
                                                                {after && (isStreaming || !markdownEnabled ? (
                                                                    <p className="whitespace-pre-wrap break-words">{after}</p>
                                                                ) : (
                                                                    <MarkdownRenderer content={after}/>
                                                                ))}
                                                            </>
                                                        );
                                                    }
                                                    return isStreaming || !markdownEnabled ? (
                                                        <p className="whitespace-pre-wrap break-words">{message.content}</p>
                                                    ) : (
                                                        <MarkdownRenderer content={message.content}/>
                                                    );
                                                })()}
                                            </div>
                                            <BubbleActions
                                                showCopy={true}
                                                showDelete={!!onDelete}
                                                onCopy={() => handleCopyText(message.content || '')}
                                                onDelete={onDelete}
                                                copied={copied}
                                            />
                                        </div>
                                    </div>
                                )}
                                {!hasContent && isStreaming && (
                                    <div
                                        className="rounded-2xl px-4 py-3 shadow-sm max-w-full bg-gray-100 dark:bg-[#1c1c20]/80 border-2 border-blue-300 dark:border-blue-500/60">
                                        <span className="inline-flex gap-0.5">
                                            <span className="w-1.5 h-3.5 bg-gray-400 dark:bg-gray-500 rounded-[1px] animate-pulse"/>
                                        </span>
                                    </div>
                                )}
                            </>
                        );
                    })()}
                </div>
            </motion.div>
        )
    }


    // ===== Error message (红色调醒目 + 单层实色质感) =====
    if (message.role === 'error') {
        if (!message.content || !message.content.trim()) return null
        return (
            <motion.div
                initial={{opacity: 0, y: 8}}
                animate={{opacity: 1, y: 0}}
                className="flex items-start gap-3 px-4 py-2 group"
            >
                <div
                    className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-red-100 dark:bg-red-900/40 ring-1 ring-red-300/40 dark:ring-red-700/40"
                    aria-hidden="true">
                    <AlertCircle className="w-4 h-4 text-red-500" />
                </div>
                <div className="flex-1 min-w-0 max-w-[90%] xs:max-w-[85%] sm:max-w-[80%] md:max-w-[75%] lg:max-w-[65%] xl:max-w-[70%] 2xl:max-w-[65%]">
                    <div className="relative rounded-xl border-l-4 border-l-red-500 dark:border-l-red-500 border border-red-200 dark:border-red-800/60 bg-red-50 dark:bg-red-950/50 shadow-md shadow-red-200/20 dark:shadow-red-950/40">
                        <BubbleActions
                            showCopy={true}
                            showDelete={!!onDelete}
                            onCopy={() => handleCopyText(message.content || '')}
                            onDelete={onDelete}
                            copied={copied}
                        />
                        <button
                            onClick={() => setErrorExpanded(!errorExpanded)}
                            className="w-full flex items-center gap-2 px-3 py-2.5 text-left transition-colors hover:bg-red-100/40 dark:hover:bg-red-950/70 rounded-r-xl"
                            aria-label={errorExpanded ? '收起错误详情' : '展开错误详情'}
                        >
                            <span className="flex items-center gap-1.5 flex-shrink-0">
                                <span className="text-[11px] font-bold uppercase tracking-wider text-red-600 dark:text-red-400 whitespace-nowrap">处理失败</span>
                            </span>
                            <div className="flex-1 min-w-0">
                                <p className="text-xs text-red-800/80 dark:text-red-300/80 truncate">
                                    {message.content}
                                </p>
                            </div>
                            <motion.span
                                animate={{ rotate: errorExpanded ? 90 : 0 }}
                                transition={{ duration: 0.2 }}
                                className="flex-shrink-0"
                            >
                                <ChevronRight className="w-4 h-4 text-red-400" />
                            </motion.span>
                        </button>
                        <AnimatePresence>
                            {errorExpanded && (
                                <motion.div
                                    initial={{ height: 0, opacity: 0 }}
                                    animate={{ height: 'auto', opacity: 1 }}
                                    exit={{ height: 0, opacity: 0 }}
                                    transition={{ duration: 0.2 }}
                                    className="border-t border-red-100 dark:border-red-800/40"
                                >
                                    <div className="p-3">
                                        <div className="text-xs text-red-900/90 dark:text-red-200/90 leading-relaxed whitespace-pre-wrap break-words font-mono bg-red-100/40 dark:bg-red-950/70 rounded-lg p-3 border border-red-200/60 dark:border-red-800/40 shadow-inner">
                                            {message.content}
                                        </div>
                                    </div>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </div>
                </div>
            </motion.div>
        )
    }
    return null
}

// ✅ 性能优化：使用 React.memo 避免历史消息随流式更新而重渲染
export default React.memo(MessageBubble)
