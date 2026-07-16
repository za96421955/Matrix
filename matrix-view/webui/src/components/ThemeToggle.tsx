import {Sun, Moon} from 'lucide-react'
import {useThemeStore} from '../store/themeStore'

export default function ThemeToggle() {
    const theme = useThemeStore((s) => s.theme)
    const toggle = useThemeStore((s) => s.toggle)

    return (
        <button
            onClick={toggle}
            aria-label={theme === 'light' ? '切换到暗色模式' : '切换到亮色模式'}
            className="rounded-lg p-2 hover:bg-gray-200 dark:hover:bg-white/[0.06] transition-colors focus-visible:outline-2 focus-visible:outline-blue-500"
        >
            {theme === 'light' ? (
                <Moon className="w-5 h-5 text-gray-600 dark:text-gray-400"/>
            ) : (
                <Sun className="w-5 h-5 text-yellow-400"/>
            )}
        </button>
    )
}
