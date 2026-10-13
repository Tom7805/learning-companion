import type { ButtonHTMLAttributes, ReactNode, Ref } from 'react'
import { cn } from '@/shared/lib/cn'

type Tone = 'ghost' | 'outline' | 'night'

const tones: Record<Tone, string> = {
  ghost: 'border-transparent text-ink-soft hover:bg-ink/5 hover:text-ink',
  outline: 'border-ink bg-surface text-ink hover:bg-paper',
  /** Trên nền tối của khung ứng dụng (thanh bên). */
  night: 'border-transparent text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
}

interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  ref?: Ref<HTMLButtonElement>
  /** Nhãn cho trình đọc màn hình; nút chỉ có biểu tượng nên bắt buộc. */
  label: string
  icon: ReactNode
  tone?: Tone
}

/** Nút chỉ có biểu tượng, vùng chạm 44 × 44. */
export function IconButton({ label, icon, tone = 'ghost', className, type = 'button', ...rest }: IconButtonProps) {
  return (
    <button
      type={type}
      aria-label={label}
      className={cn(
        'inline-grid size-11 shrink-0 place-items-center rounded-xl border-[1.5px] transition-colors',
        'disabled:cursor-not-allowed disabled:opacity-50',
        tones[tone],
        className,
      )}
      {...rest}
    >
      {icon}
    </button>
  )
}
