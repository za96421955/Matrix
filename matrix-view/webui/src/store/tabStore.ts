import {create} from 'zustand'
import {persist} from 'zustand/middleware'

export type TabType = 'ai-assistant' | 'deepseek-chat' | 'deepseek-usage' | 'deepseek-api'

interface TabState {
    activeTab: TabType
    setActiveTab: (tab: TabType) => void
}

export const useTabStore = create<TabState>()(
    persist(
        (set) => ({
            activeTab: 'ai-assistant',
            setActiveTab: (tab) => set({activeTab: tab}),
        }),
        {name: 'matrix-tab'}
    )
)
