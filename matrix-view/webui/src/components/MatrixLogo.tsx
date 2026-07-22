import { useRef } from "react"
import {useThemeStore} from '../store/themeStore'

interface MatrixLogoProps {
    size?: 'sm' | 'xl'
    className?: string
}

// ---------- 圆角竖线配置 ----------
type LineConfig = {
    viewBox: number
    count: 4
    lineWidth: number
    height: number
    spreadPct: number
    offsets: [number, number, number, number]
}

const CONFIG: Record<'sm' | 'xl', LineConfig> = {
    sm: {
        viewBox: 16,
        count: 4,
        lineWidth: 2.5,
        height: 11,
        spreadPct: 1.0,
        offsets: [0.0, 4.0, 0.0, 3.4],
    },
    xl: {
        viewBox: 64,
        count: 4,
        lineWidth: 10,
        height: 46,
        spreadPct: 1.0,
        offsets: [0.0, 12.0, 0.0, 10.0],
    },
}

export default function MatrixLogo({size = 'sm', className = ''}: MatrixLogoProps) {
    const uidRef = useRef(`ml-${Math.random().toString(36).slice(2, 10)}`)
    const theme = useThemeStore((s) => s.theme)
    const isDark = theme === 'dark'

    const cfg = CONFIG[size]
    const {viewBox, count, lineWidth, height, spreadPct, offsets} = cfg

    const padX = viewBox * 0.08
    const usableW = (viewBox - padX * 2) * spreadPct
    const startX = (viewBox - usableW) / 2
    const stepX = usableW / (count - 1)

    const top = viewBox * 0.08
    const cornerRadius = lineWidth * 0.4  // 圆角大小与线宽成比例

    const bgColor = isDark ? '#ffffff' : '#000000'
    const rainColor = isDark ? '#000000' : '#00ff41'

    const gradientId = `rainGrad-${size}-${uidRef.current}`

    return (
        <svg
            viewBox={`0 0 ${viewBox} ${viewBox}`}
            className={className}
            style={{
                width: size === 'sm' ? 16 : 64,
                height: size === 'sm' ? 16 : 64,
                display: 'block',
                flexShrink: 0,
            }}
            aria-hidden="true"
        >
            <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={rainColor} stopOpacity={0.15}/>
                    <stop offset="100%" stopColor={rainColor} stopOpacity={1}/>
                </linearGradient>
            </defs>

            <rect
                x={0}
                y={0}
                width={viewBox}
                height={viewBox}
                rx={viewBox * 0.2}
                ry={viewBox * 0.2}
                fill={bgColor}
            />

            {Array.from({length: count}).map((_, i) => {
                const x = startX + i * stepX - lineWidth / 2
                const y = top + offsets[i]
                return (
                    <rect
                        key={i}
                        x={x}
                        y={y}
                        width={lineWidth}
                        height={height}
                        rx={cornerRadius}
                        ry={cornerRadius}
                        fill={`url(#${gradientId})`}
                    />
                )
            })}
        </svg>
    )
}