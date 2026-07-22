import {motion} from 'framer-motion'

export default function TypingIndicator() {
    return (
        <div className="flex items-start gap-3 px-4 py-2" role="status" aria-label="AI 正在输入">
            <div
                className="flex-shrink-0 w-8 h-8 rounded-full dark:bg-[#252529] bg-gray-800 flex items-center justify-center text-gray-200 text-xs font-bold shadow-sm">
                M
            </div>
            <div className="bg-gray-100 dark:bg-[#1c1c20]/80 rounded-2xl rounded-tl-sm px-4 py-3 shadow-sm">
                <div className="flex gap-1.5">
                    {[0, 1, 2].map((i) => (
                        <motion.span
                            key={i}
                            className="w-2 h-2 bg-gray-400 dark:bg-gray-400 rounded-full"
                            animate={{
                                y: [0, -6, 0],
                                opacity: [0.4, 1, 0.4],
                            }}
                            transition={{
                                duration: 1,
                                repeat: Infinity,
                                delay: i * 0.2,
                                ease: 'easeInOut',
                            }}
                        />
                    ))}
                </div>
            </div>
        </div>
    )
}
