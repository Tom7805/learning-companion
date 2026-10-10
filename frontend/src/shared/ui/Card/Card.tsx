import type { HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

type Tone = 'surface' | 'sun' | 'lilac' | 'sky' | 'night'

const tones: Record<Tone, string> = {
  surface: 'bg-surface text-ink',
  sun: 'bg-sun text-ink',
  lilac: 'bg-lilac-soft text-ink',
  sky: 'bg-sky text-ink',
  night: 'bg-night text-white border-night',
}

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tone?: Tone
}

export function Card({ tone = 'surface', className, ...rest }: CardProps) {
  return <div className={cn('rounded-card border-[1.5px] border-ink', tones[tone], className)} {...rest} />
}
