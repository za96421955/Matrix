import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import remarkBreaks from 'remark-breaks'
import rehypeHighlight from 'rehype-highlight'
import CodeBlock from './CodeBlock'
import ErrorBoundary from './ErrorBoundary'
import { extractTextContent } from '../utils/extractTextContent'
import type {Components} from 'react-markdown'

/**
 * 反转义被 JSON 序列化转义的换行符等特殊字符
 * 避免后端序列化时将 \n 转成字面量 \\n 导致 Markdown 解析失败
 */

function unescapeContent(str: string): string {
    return str
        .replace(/\\n/g, '\n')
        .replace(/\\t/g, '\t')
        .replace(/\\r/g, '\r')
        .replace(/\\"/g, '"')
        .replace(/\\\\/g, '\\');
}

const components: Partial<Components> = {
    // 自定义代码块渲染
    code({className, children, ...props}) {
        const match = /language-(\w+)/.exec(className || '')
        // 使用 extractTextContent 递归提取纯文本，避免 String(children) 产生 "[object Object]"
        const codeStr = extractTextContent(children).replace(/\n$/, '')

        if (match) {
            return (
                <CodeBlock className={className}>
                    {codeStr}
                </CodeBlock>
            )
        }

        // 行内代码
        return (
            <code
                className="px-1.5 py-0.5 rounded-md bg-gray-100 dark:bg-[#1c1c20] text-pink-400 text-sm font-mono"
                {...props}
            >
                {children}
            </code>
        )
    },

    // 表格样式
    table({children}) {
        return (
            <div className="overflow-x-auto my-3">
                <table className="min-w-full border-collapse border border-gray-300 dark:border-white/[0.06] text-sm">
                    {children}
                </table>
            </div>
        )
    },

    th({children}) {
        return (
            <th className="border border-gray-300 dark:border-white/[0.06] bg-gray-100 dark:bg-[#1c1c20] px-3 py-2 font-semibold text-left">
                {children}
            </th>
        )
    },

    td({children}) {
        return (
            <td className="border border-gray-300 dark:border-white/[0.06] px-3 py-2">
                {children}
            </td>
        )
    },

    // blockquote 样式
    blockquote({children}) {
        return (
            <blockquote
                className="border-l-4 border-blue-400 dark:border-blue-500 pl-4 py-1 my-3 text-gray-600 dark:text-gray-400 italic">
                {children}
            </blockquote>
        )
    },

    // 标题样式
    h1({children}) {
        return <h1 className="text-xl font-bold my-3 pb-1 border-b border-gray-200 dark:border-white/[0.06]">{children}</h1>
    },
    h2({children}) {
        return <h2 className="text-lg font-bold my-2">{children}</h2>
    },
    h3({children}) {
        return <h3 className="text-base font-semibold my-2">{children}</h3>
    },

    // 列表样式
    ul({children}) {
        return <ul className="list-disc pl-6 my-2 space-y-1">{children}</ul>
    },
    ol({children}) {
        return <ol className="list-decimal pl-6 my-2 space-y-1">{children}</ol>
    },

    // 链接样式
    a({href, children}) {
        return (
            <a
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-600 dark:text-blue-400 hover:underline font-medium"
            >
                {children}
            </a>
        )
    },

    // 水平分割线
    hr() {
        return <hr className="my-4 border-gray-200 dark:border-white/[0.06]"/>
    },

    // 段落
    p({children}) {
        return <p className="my-2 leading-relaxed break-words">{children}</p>
    },
}

interface MarkdownRendererProps {
    content: string
}

export default function MarkdownRenderer({content}: MarkdownRendererProps) {
    return (
        <ErrorBoundary
            fallback={
                <pre className="whitespace-pre-wrap break-words text-sm leading-relaxed my-2">
                    {content}
                </pre>
            }
        >
            <div className="prose-custom">
                <ReactMarkdown
                    remarkPlugins={[remarkGfm, remarkBreaks]}
                    rehypePlugins={[rehypeHighlight]}
                    components={components}
                >
                    {unescapeContent(content)}
                </ReactMarkdown>
            </div>
        </ErrorBoundary>
    )
}
