export interface ToolCallFunction {
    name: string
    arguments: string
}

export interface ToolCall {
    id: string
    index?: number
    type?: string
    function: ToolCallFunction
}

export interface Message {
    id: string
    role: 'user' | 'assistant' | 'system' | 'tool'
    content: string
    reasoningContent?: string
    toolCalls?: ToolCall[]
    toolCallId?: string
    timestamp: number
}

export interface BackendMessageRecord {
    id: number
    userId?: number
    sessionId?: number
    role: 'user' | 'assistant' | 'system' | 'tool'
    content: string
    reasoning_content?: string | null
    tool_calls?: string | null
    tool_call_id?: string | null
    createTime: string
    updateTime?: string
}

export interface Session {
    id: string
    title: string
    messages: Message[]
    backendSessionId?: number
    createdAt: number
    updatedAt: number
}

export interface BackendSessionSummary {
    id: number
    title: string
    updateTime: string
    authLevel?: number
    agent?: string
}

export interface AgentInfo {
    name: string
    description?: string
    model?: string
    enabled?: boolean
}

export type ThemeMode = 'light' | 'dark'
export type Pattern = 'agent' | 'plan' | 'task' | 'coding' | 'information'

export interface Toast {
    id: string
    type: 'success' | 'error' | 'warning' | 'info'
    message: string
    duration?: number
}

export interface TaskInfo {
    id: number
    taskId: string
    agentName: string
    type: string
    status: string
    content: string
    result: string
    createTime: string
}
