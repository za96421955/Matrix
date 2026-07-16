import {useState, useCallback} from 'react'
import {motion, AnimatePresence} from 'framer-motion'
import {Key, Eye, EyeOff, X, Check} from 'lucide-react'
import {useApiKeyStore} from '../store/apiKeyStore'

interface ApiKeyModalProps {
    open: boolean
    onClose: () => void
}

export default function ApiKeyModal({open, onClose}: ApiKeyModalProps) {
    const savedKey = useApiKeyStore((s) => s.apiKey)
    const setApiKey = useApiKeyStore((s) => s.setApiKey)
    const [inputValue, setInputValue] = useState(savedKey)
    const [showKey, setShowKey] = useState(false)

    const handleSave = useCallback(() => {
        //确保只保存纯 token字符串，去除可能的 "Bearer "前缀和外围括号等杂物
        let cleanKey = inputValue.trim()
        //去除可能的 "Bearer "前缀
        if (cleanKey.startsWith('Bearer ')) {
            cleanKey = cleanKey.slice(7).trim()
        }
        //去除可能的引号包裹
        cleanKey = cleanKey.replace(/^["']|["']$/g, '')
        //去除可能的外围括号（像 [{...}]这种错误格式）
        if (cleanKey.startsWith('[') || cleanKey.startsWith('{')) {
            cleanKey = ''
        }
        setApiKey(cleanKey)
        onClose()
    }, [inputValue, setApiKey, onClose])

    const handleClear = useCallback(() => {
        setInputValue('')
        setApiKey('')
        onClose()
    }, [setApiKey, onClose])

    return (
        <AnimatePresence>
            {open && (
                <>
                    <motion.div
                        initial={{opacity: 0}}
                        animate={{opacity: 1}}
                        exit={{opacity: 0}}
                        onClick={onClose}
                        className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm"
                    />
                    <motion.div
                        initial={{opacity: 0, scale: 0.95, y: 20}}
                        animate={{opacity: 1, scale: 1, y: 0}}
                        exit={{opacity: 0, scale: 0.95, y: 20}}
                        transition={{type: 'spring', damping: 25, stiffness: 300}}
                        className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 w-[90vw] max-w-md rounded-2xl bg-white dark:bg-[#1c1c20] border border-gray-200 dark:border-white/[0.08] shadow-2xl p-6"
                        role="dialog"
                        aria-label="设置 API Key"
                    >
                        <div className="flex items-center justify-between mb-6">
                            <div className="flex items-center gap-3">
                                <div
                                    className="w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/40 flex items-center justify-center">
                                    <Key className="w-5 h-5 text-blue-600 dark:text-blue-400"/>
                                </div>
                                <div>
                                    <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                                        设置 API Key
                                    </h2>
                                    <p className="text-sm text-gray-500 dark:text-gray-400">
                                        输入你的 API Key以连接后端服务
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

                        <div className="space-y-4">
                            <div>
                                <label htmlFor="api-key-input"
                                       className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                                    API Key
                                </label>
                                <div className="relative">
                                    <input
                                        id="api-key-input"
                                        type={showKey ? 'text' : 'password'}
                                        value={inputValue}
                                        onChange={(e) => setInputValue(e.target.value)}
                                        placeholder="ak-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                                        className="w-full rounded-xl border border-gray-300 dark:border-white/[0.06] bg-white dark:bg-black px-4 py-3 pr-20 text-sm font-mono placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:focus:border-blue-400 transition-colors"
                                        autoFocus
                                    />
                                    <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1">
                                        <button
                                            onClick={() => setShowKey(!showKey)}
                                            aria-label={showKey ? '隐藏 API Key' : '显示 API Key'}
                                            className="rounded-lg p-2 hover:bg-gray-100 dark:hover:bg-gray-900 transition-colors"
                                            tabIndex={-1}
                                        >
                                            {showKey ? (
                                                <EyeOff className="w-4 h-4 text-gray-400"/>
                                            ) : (
                                                <Eye className="w-4 h-4 text-gray-400"/>
                                            )}
                                        </button>
                                    </div>
                                </div>
                            </div>

                            {/*安全提示 */}
                            <p className="text-xs text-gray-400 dark:text-gray-500 text-center">
                                你的 API Key将保存在浏览器本地存储中，请勿在公共电脑上使用。
                            </p>

                            <div className="flex items-center gap-3">
                                <button
                                    onClick={handleSave}
                                    disabled={!inputValue.trim()}
                                    className="flex-1 flex items-center justify-center gap-2 rounded-xl bg-blue-500 hover:bg-blue-600 disabled:bg-gray-300 dark:disabled:bg-gray-800 disabled:cursor-not-allowed text-white px-4 py-2.5 text-sm font-medium transition-colors"
                                >
                                    <Check className="w-4 h-4"/>
                                    保存
                                </button>
                                {savedKey && (
                                    <button
                                        onClick={handleClear}
                                        className="rounded-xl border border-gray-300 dark:border-white/[0.06] px-4 py-2.5 text-sm font-medium text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-900 transition-colors"
                                    >
                                        清除
                                    </button>
                                )}
                            </div>
                        </div>
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    )
}
