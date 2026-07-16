import {useState, useRef, useCallback, useEffect} from 'react'
import {Send, Square, Brain} from 'lucide-react'
import {useChatStore} from '../store/chatStore'

interface InputBarProps {
    onSend: (text: string) => void
    onStop: () => void
    hideTopBorder?: boolean
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

    // Auto-resize textarea
    useEffect(() => {
        const ta = textareaRef.current
        if (ta) {
            ta.style.height = 'auto'
            ta.style.height = Math.min(Math.max(ta.scrollHeight, 40), 200) + 'px'
        }
    }, [text])

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
        if ((e.metaKey || e.altKey || e.shiftKey || e.ctrlKey) && e.key === 'Enter') {
            e.preventDefault()
            handleSend()
        } else if (e.key === 'Escape') {
            ;(e.target as HTMLTextAreaElement).blur()
        }
    }, [handleSend])

    return (
        <div className={`${hideTopBorder ? '' : 'border-t border-gray-200 dark:border-white/[0.04]'} bg-white dark:bg-[#18181B]/80 backdrop-blur-xl`}>
            <div className="max-w-4xl lg:max-w-5xl xl:max-w-6xl2xl:max-w-7xl mx-auto px-3 sm:px-4 py-2 sm:py-3">
                <div
                    className="relative rounded-2xl border border-gray-300 dark:border-gray-700 bg-white dark:bg-black shadow-sm transition-colors focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-500/20 dark:focus-within:border-blue-500">
                    <textarea
                        ref={textareaRef}
                        id="chat-input"
                        rows={1}
                        value={text}
                        onChange={(e) => setText(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="输入消息，Enter换行，Ctrl+Enter发送"
                        disabled={isStreaming || isBackendGenerating}
                        className="w-full resize-none bg-transparent px-4 pt-3 pb-1 text-sm placeholder:text-gray-400 dark:placeholder:text-gray-500 outline-none disabled:opacity-50 disabled:cursor-not-allowed"
                        style={{minHeight: '40px', maxHeight: '200px'}}
                        aria-disabled={isStreaming || isBackendGenerating}
                    />

                    <div className="flex items-center justify-between px-3 pb-2">
                        <div className="flex items-center gap-2 flex-wrap">
                            {/* flash/pro 模型选择 */}
                            <button
                                onClick={() => setModelType(modelType === 'flash' ? 'pro' : 'flash')}
                                className={
                                    'flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ' +
                                    (modelType === 'pro'
                                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                        : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.04]')
                                }
                                aria-label={modelType === 'pro' ? '切换为 flash' : '切换为 pro'}
                            >
                                {modelType === 'pro' ? 'pro' : 'flash'}
                            </button>

                            {/*深度思考按钮 */}
                            <button
                                onClick={() => setThinkingType(thinkingType === 'enabled' ? 'disabled' : 'enabled')}
                                className={
                                    'flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ' +
                                    (thinkingType === 'enabled'
                                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                        : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.04]')
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
                                                : 'bg-transparent text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-white/[0.04]')
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
                                className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-black px-2 py-1.5 text-xs font-medium text-gray-600 dark:text-gray-400 outline-none focus:ring-2 focus:ring-blue-500/50 cursor-pointer"
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
