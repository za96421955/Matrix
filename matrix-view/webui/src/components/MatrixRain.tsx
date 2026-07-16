import { useRef, useEffect, useCallback } from 'react'
import { useChatStore } from '../store/chatStore'
import { useEffectStore } from '../store/effectStore'
import { useThemeStore } from '../store/themeStore'

// 字符集
const CHARS = '天地玄黄陳岚熙龍CCABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%^&*()_+-=[]{}|;:,.<>?/'

interface Drop {
  x: number
  y: number
  speed: number
  length: number
  chars: string[]
  fontSize: number    // 随机大小，营造纵深
  opacity: number     // 随机透明度，配合纵深
}

export default function MatrixRain() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const dropsRef = useRef<Drop[]>([])
  const rafRef = useRef<number>(0)
  const lastTimeRef = useRef(0)
  const clearTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const prevActiveRef = useRef(false)

  const isBackendGenerating = useChatStore((s) => s.isBackendGenerating)
  const matrixRainEnabled = useEffectStore((s) => s.matrixRainEnabled)
  const theme = useThemeStore((s) => s.theme)

  const isActive = matrixRainEnabled && isBackendGenerating && theme === 'dark'

  // 当 isActive 变化时，管理延迟清空定时器，配合 CSS opacity 过渡实现淡入淡出
  useEffect(() => {
    if (!prevActiveRef.current && isActive) {
      // 从不活跃变为活跃：取消可能的延迟清空，画布上的旧内容直接复用
      if (clearTimerRef.current !== null) {
        clearTimeout(clearTimerRef.current)
        clearTimerRef.current = null
      }
    } else if (prevActiveRef.current && !isActive) {
      // 从活跃变为不活跃：启动延迟清空定时器，匹配 CSS transition 时间
      clearTimerRef.current = setTimeout(() => {
        const canvas = canvasRef.current
        if (canvas) {
          const ctx = canvas.getContext('2d')
          ctx?.clearRect(0, 0, canvas.width, canvas.height)
        }
        clearTimerRef.current = null
      }, 800)
    }
    prevActiveRef.current = isActive

    return () => {
      if (clearTimerRef.current !== null) {
        clearTimeout(clearTimerRef.current)
        clearTimerRef.current = null
      }
    }
  }, [isActive])

  const initDrops = useCallback((width: number, height: number) => {
    // 密集度：每列最多 4 道雨，营造满屏效果
    const cols = Math.floor(width / 10)
    const baseFontSize = Math.min(22, Math.max(12, width / 80))
    const drops: Drop[] = []

    for (let i = 0; i < cols; i++) {
      // 随机偏移 x，让字幕不严格对齐网格，更自然
      const xOffset = Math.random() * 8 - 4
      // 随机大小：0.6x ~ 1.4x 基准字号 -> 前后纵深
      const fontSizeScale = 0.6 + Math.random() * 0.8
      const fontSize = Math.max(8, Math.min(28, baseFontSize * fontSizeScale))

      // 跳过一些位置制造疏密变化
      if (Math.random() < 0.2) continue

      const length = Math.floor(Math.random() * 20) + 8 // 8-27 字符长
      drops.push({
        x: i * 10 + xOffset,
        y: Math.random() * height - height * 0.2,
        speed: Math.random() * 4 + 1.5,           // 1.5~5.5 不同下落速度
        length,
        fontSize,
        opacity: Math.random() * 0.4 + 0.6,       // 0.6~1.0 透明度层次
        chars: Array.from({ length }, () => CHARS[Math.floor(Math.random() * CHARS.length)]),
      })
    }
    dropsRef.current = drops
  }, [])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const resize = () => {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight
      if (dropsRef.current.length === 0) {
        initDrops(canvas.width, canvas.height)
      }
    }

    resize()
    window.addEventListener('resize', resize)

    const draw = (timestamp: number) => {
      if (!isActive) {
        // 不活跃时不绘制新内容，但保持画布最后帧，不清除
        // CSS opacity 控制淡出（1->0），延迟定时器在过渡完成后清空
        rafRef.current = requestAnimationFrame(draw)
        return
      }

      // 约 20fps，兼顾流畅和性能
      if (timestamp - lastTimeRef.current < 50) {
        rafRef.current = requestAnimationFrame(draw)
        return
      }
      lastTimeRef.current = timestamp

      // === 关键: 完全清除画布，不叠加任何背景色 ===
      // 通过 clearRect 让 Canvas 保持透明，UI 界面始终可见
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      for (const drop of dropsRef.current) {
        const { x, fontSize, opacity } = drop

        for (let i = 0; i < drop.length; i++) {
          const y = drop.y - i * fontSize * 1.1
          if (y < -fontSize || y > canvas.height + fontSize) continue

          // 头部到尾部亮度递减，配合透明度实现拖尾效果
          const t = i / drop.length                    // 0~1 尾部方向
          const brightness = 1 - t                     // 头部亮 -> 尾部暗
          const charAlpha = opacity * brightness * 0.85

          // 头部亮白绿，尾部暗绿，投影在透明背景上
          if (i === 0) {
            ctx.fillStyle = `rgba(180, 255, 180, ${charAlpha})`
          } else {
            const g = Math.floor(180 * brightness + 40)
            ctx.fillStyle = `rgba(40, ${g}, 40, ${charAlpha * 0.7})`
          }

          ctx.font = `bold ${fontSize * 1.1}px "Courier New","PingFang SC","Hiragino Sans","Apple SD Gothic Neo","Nirmala UI","Leelawadee UI","Sylfaen",monospace`
          ctx.fillText(drop.chars[i], x, y)
        }

        // 下落
        drop.y += drop.speed * 2

        // 到底部后重置到顶部，并随机更新参数
        if (drop.y - drop.length * fontSize * 1.1 > canvas.height) {
          drop.y = -(drop.length * fontSize * 1.1) - Math.random() * 100
          drop.speed = Math.random() * 4 + 1.5
          drop.length = Math.floor(Math.random() * 20) + 8
          drop.fontSize = Math.max(8, Math.min(28, (Math.min(22, Math.max(12, canvas.width / 80)) * (0.6 + Math.random() * 0.8))))
          drop.opacity = Math.random() * 0.4 + 0.6
          drop.chars = Array.from({ length: drop.length }, () => CHARS[Math.floor(Math.random() * CHARS.length)])
        }

        // 每个字符每帧都随机变换，实现"不停变换"的效果
        for (let i = 0; i < drop.length; i++) {
          drop.chars[i] = CHARS[Math.floor(Math.random() * CHARS.length)]
        }
      }

      rafRef.current = requestAnimationFrame(draw)
    }

    rafRef.current = requestAnimationFrame(draw)

    return () => {
      cancelAnimationFrame(rafRef.current)
      window.removeEventListener('resize', resize)
    }
  }, [isActive, initDrops])

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 w-full h-full z-50 pointer-events-none"
      style={{
        opacity: isActive ? 1 : 0,
        transition: 'opacity 0.3s ease-in-out',
      }}
      aria-hidden="true"
    />
  )
}
