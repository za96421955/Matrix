import {useState, useCallback} from 'react'
import {Copy, Check} from 'lucide-react'

interface CodeBlockProps {
    className?: string
    children?: React.ReactNode
}

export default function CodeBlock({className, children}: CodeBlockProps) {
    const [copied, setCopied] = useState(false)

    // 从 className 中提取语言，格式如 "language-typescript"
    const language = className?.replace('language-', '') ?? ''
    // 提取代码文本
    const code = typeof children === 'string' ? children : ''

    const handleCopy = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(code)
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
        } catch {
            // fallback
            const ta = document.createElement('textarea')
            ta.value = code
            document.body.appendChild(ta)
            ta.select()
            document.execCommand('copy')
            document.body.removeChild(ta)
            setCopied(true)
            setTimeout(() => setCopied(false), 2000)
        }
    }, [code])

    return (
        <div className="relative group my-3 rounded-lg overflow-hidden border border-gray-200 dark:border-white/[0.06]">
            {/* 头部：语言标签 + 复制按钮 */}
            <div
                className="flex items-center justify-between px-4 py-2 bg-gray-50 dark:bg-[#131316] border-b border-gray-200 dark:border-white/[0.06]">
        <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
          {language || 'code'}
        </span>
                <button
                    onClick={handleCopy}
                    aria-label={copied ? '已复制' : '复制代码'}
                    className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 transition-colors rounded px-2 py-1 hover:bg-gray-200 dark:hover:bg-white/[0.04]"
                >
                    {copied ? (
                        <>
                            <Check className="w-3.5 h-3.5 text-green-500"/>
                            <span className="text-green-500">已复制</span>
                        </>
                    ) : (
                        <>
                            <Copy className="w-3.5 h-3.5"/>
                            <span>复制</span>
                        </>
                    )}
                </button>
            </div>
            {/* 代码内容 */}
            <pre className="overflow-x-auto max-w-full p-4 text-sm leading-relaxed bg-white dark:bg-[#131316]">
        <code className={className}>{children}</code>
      </pre>
        </div>
    )
}
