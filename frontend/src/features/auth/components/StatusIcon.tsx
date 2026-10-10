import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

type Tone = 'sun' | 'lilac' | 'sky' | 'success' | 'danger'

const tones: Record<Tone, string> = {
  sun: 'bg-sun',
  lilac: 'bg-lilac',
  sky: 'bg-sky',
  success: 'bg-success-soft',
  danger: 'bg-danger-soft',
}

/** Khối biểu tượng lớn đầu mỗi màn hình trạng thái, cùng phong cách viền mực của mẫu. */
export function StatusIcon({ tone, children, dot }: { tone: Tone; children: ReactNode; dot?: boolean }) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        'relative grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink animate-pop',
        tones[tone],
      )}
    >
      {children}
      {dot && <span className="absolute -right-1.5 -top-1.5 size-4 rounded-full border-[1.5px] border-ink bg-accent" />}
    </div>
  )
}
