import React, {useEffect, Component, ErrorInfo, ReactNode, lazy} from 'react'
import {useThemeStore} from './store/themeStore'
import Sidebar from './components/Sidebar'
import ChatArea from './components/ChatArea'
import TabBar from './components/TabBar'
import ToastContainer from './components/Toast'

const MatrixRain = lazy(() => import('./components/MatrixRain'))

interface ErrorBoundaryProps {
    children: ReactNode
}

interface ErrorBoundaryState {
    hasError: boolean
    error: Error | null
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    constructor(props: ErrorBoundaryProps) {
        super(props)
        this.state = {hasError: false, error: null}
    }

    static getDerivedStateFromError(error: Error): ErrorBoundaryState {
        return {hasError: true, error}
    }

    componentDidCatch(error: Error, _errorInfo: ErrorInfo) {
        console.error('[ErrorBoundary]', error, _errorInfo)
    }

    render() {
        if (this.state.hasError) {
            return (
                <div className="flex h-full w-full items-center justify-center bg-white dark:bg-[#18181B]">
                    <div className="text-center px-6 max-w-md">
                        <div className="text-5xl mb-4">😵</div>
                        <h2 className="text-xl font-bold text-gray-800 dark:text-gray-100 mb-2">
                            出错了
                        </h2>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
                            应用遇到了一个意外错误，请尝试刷新页面。
                        </p>
                        <p className="text-xs text-gray-400 dark:text-gray-500 mb-6 font-mono break-all">
                            {this.state.error?.message}
                        </p>
                        <button onClick={() => window.location.reload()}
                                className="rounded-lg bg-blue-500 hover:bg-blue-600 text-white px-5 py-2 text-sm font-medium transition-colors"
                        >
                            刷新页面
                        </button>
                    </div>
                </div>
            )
        }
        return this.props.children
    }
}

export default function App() {
    const theme = useThemeStore((s) => s.theme)

    useEffect(() => {
        document.documentElement.classList.toggle('dark', theme === 'dark')
    }, [theme])

    return (
        <ErrorBoundary>
            {/* Matrix Rain 背景层 - 仅暗色模式下显示 */}
            {theme === 'dark' && (
                <React.Suspense fallback={null}>
                    <MatrixRain />
                </React.Suspense>
            )}

            <div
                className="relative z-10 flex flex-col h-full w-full overflow-hidden bg-white dark:bg-[#18181B] text-gray-900 dark:text-gray-200 transition-colors">
                <TabBar/>
                <div className="flex-1 flex overflow-hidden w-full">
                    <Sidebar/>
                    <ChatArea/>
                </div>
            </div>
            <ToastContainer/>
        </ErrorBoundary>
    )
}
