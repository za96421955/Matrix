import { Component, ReactNode, ErrorInfo } from 'react'

interface ErrorBoundaryProps {
    children: ReactNode
    fallback: ReactNode
}

interface ErrorBoundaryState {
    hasError: boolean
}

/**
 * React Error Boundary
 * 捕获子组件渲染过程中的异常，降级展示 fallback 内容
 * 用于 MarkdownRenderer 中，当 react-markdown 解析/渲染出错时降级为纯文本
 */
class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    constructor(props: ErrorBoundaryProps) {
        super(props)
        this.state = { hasError: false }
    }

    static getDerivedStateFromError(): ErrorBoundaryState {
        return { hasError: true }
    }

    componentDidCatch(error: Error, _errorInfo: ErrorInfo): void {
        console.warn('[MarkdownRenderer] 渲染异常，已降级为纯文本:', error.message)
    }

    render() {
        if (this.state.hasError) {
            return this.props.fallback
        }
        return this.props.children
    }
}

export default ErrorBoundary
