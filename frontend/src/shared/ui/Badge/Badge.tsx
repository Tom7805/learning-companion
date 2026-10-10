import type { HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

type Tone = 'ink' | 'sun' | 'lilac' | 'sky' | 'outline'

const tones: Record<Tone, string> = {
  ink: 'bg-ink text-white border-ink',
  sun: 'bg-sun text-ink border-ink',
  lilac: 'bg-lilac text-ink border-ink',
  sky: 'bg-sky text-ink border-ink',
  outline: 'bg-surface text-ink border-ink',
}

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone
}

/** Nhãn nhỏ kiểu "Marketing" / "Computer Science" trên thẻ của mẫu. */
export function Badge({ tone = 'ink', className, ...rest }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-lg border-[1.5px] px-2.5 py-1 text-xs font-medium',
        tones[tone],
        className,
      )}
      {...rest}
    />
  )
}
