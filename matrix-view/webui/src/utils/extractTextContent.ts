import { ReactNode } from 'react'

/**
 * 递归提取 ReactNode 中的纯文本内容
 *
 * react-markdown v9 的 code 组件中，children 为 ReactNode 类型（可能包含 ReactElement 数组），
 * 而非纯字符串。直接使用 String(children) 会导致 "[object Object]" 错误。
 * 此函数递归遍历所有节点，提取纯文本字符串。
 *
 * @param node - ReactNode 节点
 * @returns 提取出的纯文本字符串
 */
export function extractTextContent(node: ReactNode): string {
    if (node == null || node === false) return ''

    if (typeof node === 'string' || typeof node === 'number') {
        return String(node)
    }

    if (Array.isArray(node)) {
        return node.map(extractTextContent).join('')
    }

    // ReactElement 或类似对象节点，递归提取其 children
    if (typeof node === 'object' && node !== null) {
        const element = node as { props?: { children?: ReactNode } }
        if (element.props?.children !== undefined) {
            return extractTextContent(element.props.children)
        }
    }

    return ''
}
