import {motion, AnimatePresence} from 'framer-motion'
import {X, CheckCircle, AlertCircle, AlertTriangle, Info} from 'lucide-react'
import {useToastStore} from '../store/toastStore'

const iconMap = {
    success: CheckCircle,
    error: AlertCircle,
    warning: AlertTriangle,
    info: Info,
}

const colorMap = {
    success: 'bg-green-50 dark:bg-green-900/15 border-green-200 dark:border-green-800/30 text-green-800 dark:text-green-200',
    error: 'bg-red-50 dark:bg-red-900/15 border-red-200 dark:border-red-800/30 text-red-800 dark:text-red-200',
    warning: 'bg-yellow-50 dark:bg-yellow-900/15 border-yellow-200 dark:border-yellow-800/30 text-yellow-800 dark:text-yellow-200',
    info: 'bg-blue-50 dark:bg-blue-900/15 border-blue-200 dark:border-blue-800/30 text-blue-800 dark:text-blue-200',
}

export default function ToastContainer() {
    const toasts = useToastStore((s) => s.toasts)
    const removeToast = useToastStore((s) => s.removeToast)

    return (
        <div className="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm w-full pointer-events-none">
            <AnimatePresence>
                {toasts.map((toast) => {
                    const Icon = iconMap[toast.type]
                    return (
                        <motion.div
                            key={toast.id}
                            initial={{opacity: 0, x: 100, scale: 0.9}}
                            animate={{opacity: 1, x: 0, scale: 1}}
                            exit={{opacity: 0, x: 100, scale: 0.9}}
                            transition={{type: 'spring', damping: 20, stiffness: 300}}
                            className={`pointer-events-auto flex items-start gap-3 rounded-xl border p-4 shadow-lg ${colorMap[toast.type]}`}
                            role="alert"
                        >
                            <Icon className="w-5 h-5 flex-shrink-0 mt-0.5"/>
                            <p className="flex-1 text-sm font-medium">{toast.message}</p>
                            <button
                                onClick={() => removeToast(toast.id)}
                                aria-label="关闭提示"
                                className="flex-shrink-0 rounded-lg p-1 hover:bg-black/10 dark:hover:bg-white/[0.06] transition-colors"
                            >
                                <X className="w-4 h-4"/>
                            </button>
                        </motion.div>
                    )
                })}
            </AnimatePresence>
        </div>
    )
}
