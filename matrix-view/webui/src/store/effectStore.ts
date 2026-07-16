import {create} from 'zustand'
import {persist} from 'zustand/middleware'

interface EffectState {
  matrixRainEnabled: boolean
  toggleMatrixRain: () => void
  setMatrixRainEnabled: (v: boolean) => void
}

export const useEffectStore = create<EffectState>()(
  persist(
    (set) => ({
      matrixRainEnabled: false,
      toggleMatrixRain: () => set((s) => ({ matrixRainEnabled: !s.matrixRainEnabled })),
      setMatrixRainEnabled: (v) => set({ matrixRainEnabled: v }),
    }),
    { name: 'matrix-effects' }
  )
)
