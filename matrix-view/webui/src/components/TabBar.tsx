import {useState, useRef, useCallback} from 'react'
import {motion, AnimatePresence} from 'framer-motion'
import {Bot} from 'lucide-react'
import {useTabStore} from '../store/tabStore'

const tabs: {id: 'ai-assistant' | 'deepseek-chat' | 'deepseek-usage' | 'deepseek-api'; label: string; url?: string; icon: React.ReactNode}[] = [
    {
        id: 'ai-assistant',
        label: 'AI 助手',
        icon: <Bot className="w-5 h-5"/>,
    },
    {
        id: 'deepseek-chat',
        label: 'DeepSeek Chat',
        url: 'https://chat.deepseek.com/',
        icon: (
            <img
                src="./deepseek-chat-favicon.svg"
                alt=""
                className="w-5 h-5"
            />
        ),
    },
    {
        id: 'deepseek-api',
        label: 'DeepSeek API',
        url: 'https://api-docs.deepseek.com/zh-cn/quick_start/pricing/#%E6%89%A3%E8%B4%B9%E8%A7%84%E5%88%99',
        icon: (
            <img
                src="./deepseek-api-favicon.svg"
                alt=""
                className="w-5 h-5"
            />
        ),
    },
    {
        id: 'deepseek-usage',
        label: '用量统计',
        url: 'https://platform.deepseek.com/usage',
        icon: (
            <img
                src="./deepseek-platform-favicon.svg"
                alt=""
                className="w-5 h-5 dark:brightness-0 dark:invert"
            />
        ),
    },
]

export default function TabBar() {
    const [visible, setVisible] = useState(false)
    const hideTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
    const isHoveringRef = useRef(false)
    const activeTab = useTabStore((s) => s.activeTab)
    const setActiveTab = useTabStore((s) => s.setActiveTab)

    const scheduleHide = useCallback(() => {
        if (hideTimerRef.current) {
            clearTimeout(hideTimerRef.current)
        }
        hideTimerRef.current = setTimeout(() => {
            if (!isHoveringRef.current) {
                setVisible(false)
            }
        }, 200)
    }, [])

    const handleMouseEnter = useCallback(() => {
        isHoveringRef.current = true
        if (hideTimerRef.current) {
            clearTimeout(hideTimerRef.current)
            hideTimerRef.current = null
        }
        setVisible(true)
    }, [])

    const handleMouseLeave = useCallback(() => {
        isHoveringRef.current = false
        scheduleHide()
    }, [scheduleHide])

    const handleTriggerEnter = useCallback(() => {
        setVisible(true)
    }, [])

    const handleTabClick = useCallback((tabId: 'ai-assistant' | 'deepseek-chat' | 'deepseek-usage' | 'deepseek-api', url?: string) => {
        if (tabId === 'ai-assistant') {
            setActiveTab('ai-assistant')
        } else if (url) {
            window.open(url, '_blank', 'noopener,noreferrer')
        }
    }, [setActiveTab])

    return (
        <>
            {/* 顶部 3px 透明触发条 */}
            <div
                className="fixed top-0 left-0 right-0 z-50 h-1"
                onMouseEnter={handleTriggerEnter}
            />

            {/* TabBar 主体 */}
            <AnimatePresence>
                {visible && (
                    <motion.nav
                        key="tab-bar"
                        className="fixed top-0 left-0 right-0 z-50 flex items-center gap-1 px-4 h-12 bg-white/80 dark:bg-[#18181B]/85 backdrop-blur-xl border-b border-gray-200 dark:border-white/[0.06] select-none shadow-sm"
                        initial={{y: '-100%'}}
                        animate={{y: 0}}
                        exit={{y: '-100%'}}
                        transition={{type: 'spring', stiffness: 400, damping: 30, mass: 1}}
                        onMouseEnter={handleMouseEnter}
                        onMouseLeave={handleMouseLeave}
                    >
                        {tabs.map((tab) => {
                            const isActive = tab.id === activeTab
                            return (
                                <button
                                    key={tab.id}
                                    onClick={() => handleTabClick(tab.id, tab.url)}
                                    className={`
                                        relative flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium
                                        transition-colors duration-200
                                        ${isActive
                                            ? 'text-blue-600 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/20'
                                            : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/[0.06]'
                                        }
                                    `}
                                >
                                    {tab.icon}
                                    <span className="hidden sm:inline">{tab.label}</span>
                                </button>
                            )
                        })}
                    </motion.nav>
                )}
            </AnimatePresence>
        </>
    )
}
