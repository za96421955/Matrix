import {useState, useCallback} from 'react'
import {motion, AnimatePresence} from 'framer-motion'
import {ShieldCheck, X, Check, Clock, Loader2, AlertTriangle} from 'lucide-react'
import {useTaskAuthStore} from '../store/taskAuthStore'
import type {TaskInfo} from '../types'

interface TaskAuthModalProps {
    open: boolean
    onClose: () => void
}

function formatRelativeTime(dateStr: string): string {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return dateStr
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return '刚刚'
    if (diffMin < 60) return diffMin + '分钟前'
    const diffHour = Math.floor(diffMin / 60)
    if (diffHour < 24) return diffHour + '小时前'
    const diffDay = Math.floor(diffHour / 24)
    if (diffDay < 7) return diffDay + '天前'
    return d.toLocaleDateString('zh-CN')
}

function getTypeBadge(type: string): { label: string; color: string } {
    const map: Record<string, { label: string; color: string }> = {
        query: {label: '信息查询', color: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'},
        scene: {label: '场景执行', color: 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300'},
        alarm: {label: '告警处理', color: 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300'},
    }
    return map[type] || {label: type || '未知', color: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'}
}

interface TaskItemProps {
    task: TaskInfo
    onApprove: (taskId: string) => Promise<void>
    onReject: (taskId: string, reason: string) => Promise<void>
}

function TaskItem({task, onApprove, onReject}: TaskItemProps) {
    const [rejecting, setRejecting] = useState(false)
    const [reason, setReason] = useState('用户拒绝执行')
    const [submitting, setSubmitting] = useState<'none' | 'approve' | 'reject'>('none')
    const badge = getTypeBadge(task.type)

    const handleApprove = useCallback(async () => {
        if (submitting !== 'none') return
        setSubmitting('approve')
        try {
            await onApprove(task.taskId)
        } finally {
            setSubmitting('none')
        }
    }, [task.taskId, onApprove, submitting])

    const handleReject = useCallback(async () => {
        if (submitting !== 'none') return
        if (!reason.trim()) return
        setSubmitting('reject')
        try {
            await onReject(task.taskId, reason.trim())
            setRejecting(false)
            setReason('')
        } finally {
            setSubmitting('none')
        }
    }, [task.taskId, reason, onReject, submitting])

    const isLoading = submitting !== 'none'

    return (
        <div
            className="rounded-xl border border-gray-200 dark:border-white/[0.06] bg-white dark:bg-[#1c1c20] p-4 transition-colors">
            <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0 space-y-2">
                    {/**第一行：taskId +类型标签 + agentName */}
                    <div className="flex items-center gap-2 flex-wrap">
 <span
     className="font-mono text-xs text-gray-400 dark:text-gray-500 truncate max-w-[150px]"
     title={task.taskId}
 >
 {task.taskId.slice(0, 12)}...
 </span>
                        <span
                            className={"inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium " + badge.color}>
 {badge.label}
 </span>
                        {task.agentName && (
                            <span
                                className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400">
 {task.agentName}
 </span>
                        )}
                    </div>
                    {/**第二行：任务内容（限制三行，悬停显示全文） */}
                    <p
                        className="text-sm text-gray-700 dark:text-gray-300 line-clamp-3 leading-relaxed break-words"
                        title={task.content}
                    >
                        {task.content}
                    </p>
                    {/**第三行：创建时间 */}
                    <div className="flex items-center gap-1.5 text-xs text-gray-400 dark:text-gray-500">
                        <Clock className="w-3 h-3"/>
                        {formatRelativeTime(task.createTime)}
                    </div>
                </div>
                {/**右侧：操作按钮 */}
                <div className="flex flex-col gap-2 flex-shrink-0">
                    <button
                        onClick={handleApprove}
                        disabled={isLoading}
                        className="flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300 hover:bg-green-100 dark:hover:bg-green-900/50 border border-green-200 dark:border-green-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="通过"
                    >
                        {submitting === 'approve' ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin"/>
                        ) : (
                            <Check className="w-3.5 h-3.5"/>
                        )}
                        <span className="hidden sm:inline">通过</span>
                    </button>
                    {!rejecting ? (
                        <button
                            onClick={() => setRejecting(true)}
                            disabled={isLoading}
                            className="flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 hover:bg-red-100 dark:hover:bg-red-900/50 border border-red-200 dark:border-red-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title="拒绝"
                        >
                            <X className="w-3.5 h-3.5"/>
                            <span className="hidden sm:inline">拒绝</span>
                        </button>
                    ) : null}
                </div>
            </div>

            {/**拒绝原因输入区域 */}
            {rejecting && (
                <motion.div
                    initial={{height: 0, opacity: 0}}
                    animate={{height: 'auto', opacity: 1}}
                    transition={{duration: 0.2}}
                    className="mt-3 pt-3 border-t border-gray-200 dark:border-white/[0.06] overflow-hidden"
                >
                    <div className="space-y-2">
 <textarea
     value={reason}
     onChange={(e) => setReason(e.target.value)}
     placeholder="请输入拒绝原因..."
     rows={2}
     className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-black px-3 py-2 text-sm placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:border-red-400 focus:ring-2 focus:ring-red-500/20 dark:focus:border-red-500 transition-colors resize-none outline-none"
 />
                        <div className="flex items-center justify-end gap-2">
                            <button
                                onClick={() => {
                                    setRejecting(false);
                                    setReason('')
                                }}
                                disabled={isLoading}
                                className="rounded-lg px-3 py-1.5 text-xs font-medium text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.04] transition-colors disabled:opacity-50"
                            >
                                取消
                            </button>
                            <button
                                onClick={handleReject}
                                disabled={isLoading || !reason.trim()}
                                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium bg-red-600 hover:bg-red-700 disabled:bg-gray-300 dark:disabled:bg-gray-800 disabled:cursor-not-allowed text-white transition-colors"
                            >
                                {submitting === 'reject' ? (
                                    <Loader2 className="w-3.5 h-3.5 animate-spin"/>
                                ) : (
                                    <X className="w-3.5 h-3.5"/>
                                )}
                                确认拒绝
                            </button>
                        </div>
                    </div>
                </motion.div>
            )}
        </div>
    )
}

export default function TaskAuthModal({open, onClose}: TaskAuthModalProps) {
    const taskList = useTaskAuthStore((s) => s.taskList)
    const loading = useTaskAuthStore((s) => s.loading)
    const submitAuth = useTaskAuthStore((s) => s.submitAuth)

    const handleApprove = useCallback(async (taskId: string) => {
        await submitAuth(taskId, true)
    }, [submitAuth])

    const handleReject = useCallback(async (taskId: string, reason: string) => {
        await submitAuth(taskId, false, reason)
    }, [submitAuth])

    return (
        <AnimatePresence>
            {open && (
                <motion.div
                    key="task-auth-modal"
                    initial={{opacity: 0, x: 24, y: 24}}
                    animate={{opacity: 1, x: 0, y: 0}}
                    exit={{opacity: 0, x: 24, y: 24}}
                    transition={{type: 'spring', damping: 25, stiffness: 300}}
                    className="fixed right-5 bottom-5 z-50 w-[min(440px,92vw)] max-w-md max-h-[70vh] flex flex-col rounded-2xl bg-white dark:bg-[#1c1c20] border border-gray-200 dark:border-white/[0.08] shadow-2xl"
                    role="dialog"
                    aria-label="任务授权"
                >
                    {/** Header */}
                    <div
                        className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-white/[0.08] flex-shrink-0">
                        <div className="flex items-center gap-3">
                            <div
                                className="w-10 h-10 rounded-full bg-orange-100 dark:bg-orange-900/40 flex items-center justify-center">
                                <ShieldCheck className="w-5 h-5 text-orange-600 dark:text-orange-400"/>
                            </div>
                            <div>
                                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">任务授权</h2>
                                <p className="text-xs text-gray-500 dark:text-gray-400">
                                    {loading ? '加载中...' : taskList.length > 0 ? taskList.length + '个任务待处理' : '暂无待授权任务'}
                                </p>
                            </div>
                        </div>
                        <button
                            onClick={onClose}
                            aria-label="关闭"
                            className="rounded-lg p-2 hover:bg-gray-100 dark:hover:bg-gray-900 transition-colors"
                        >
                            <X className="w-5 h-5 text-gray-500"/>
                        </button>
                    </div>

                    {/** Body:任务列表 */}
                    <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3 min-h-0">
                        {loading && taskList.length === 0 ? (
                            <div className="flex items-center justify-center py-12">
                                <Loader2 className="w-6 h-6 animate-spin text-gray-400"/>
                            </div>
                        ) : taskList.length === 0 ? (
                            <div className="flex flex-col items-center justify-center py-12 text-center">
                                <AlertTriangle className="w-12 h-12 text-gray-300 dark:text-gray-600 mb-3"/>
                                <p className="text-sm text-gray-500 dark:text-gray-400">暂无待授权任务</p>
                                <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">所有任务已处理完毕</p>
                            </div>
                        ) : (
                            taskList.map((task) => (
                                <TaskItem
                                    key={task.taskId}
                                    task={task}
                                    onApprove={handleApprove}
                                    onReject={handleReject}
                                />
                            ))
                        )}
                    </div>

                    {/** Footer */}
                    <div
                        className="flex-shrink-0 px-5 py-3 border-t border-gray-200 dark:border-white/[0.08] flex justify-end">
                        <button
                            onClick={onClose}
                            className="rounded-lg px-4 py-2 text-sm font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-900 transition-colors"
                        >
                            关闭
                        </button>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    )
}