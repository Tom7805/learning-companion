import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/** Phím tắt bàn phím, ví dụ "Ctrl K" cạnh nút Mở nhanh. */
export function Kbd({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <kbd
      className={cn(
        'inline-flex h-6 min-w-6 items-center justify-center rounded-md border border-current/25 px-1.5',
        'font-sans text-[0.6875rem] font-semibold leading-none',
        className,
      )}
    >
      {children}
    </kbd>
  )
}
