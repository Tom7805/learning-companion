import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

/**
 * Chú thích nổi khi rê chuột hoặc dùng phím Tab vào phần tử bên trong (bấm chuột thì không hiện thừa), ví dụ tên
 * mục ở thanh bên đã thu gọn. Chỉ để nhìn: phần tử bên trong phải tự có nhãn (aria-label) cho trình đọc màn hình.
 */
export function Tooltip({ label, side = 'right', children, className }: {
  label: string
  side?: 'right' | 'top'
  children: ReactNode
  className?: string
}) {
  return (
    <span className={cn('group/tooltip relative inline-flex', className)}>
      {children}
      <span
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute z-50 whitespace-nowrap rounded-lg bg-ink px-2.5 py-1.5 text-xs font-medium',
          'text-on-ink opacity-0 shadow-pop transition-opacity duration-150',
          'group-hover/tooltip:opacity-100 group-has-[:focus-visible]/tooltip:opacity-100',
          side === 'right' ? 'left-full top-1/2 ml-2 -translate-y-1/2' : 'bottom-full left-1/2 mb-2 -translate-x-1/2',
        )}
      >
        {label}
      </span>
    </span>
  )
}
