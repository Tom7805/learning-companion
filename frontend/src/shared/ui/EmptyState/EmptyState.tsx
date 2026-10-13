import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

interface EmptyStateProps {
  icon: ReactNode
  title: string
  description?: ReactNode
  action?: ReactNode
  /** "compact" cho chỗ hẹp như thanh bên. */
  size?: 'default' | 'compact'
  className?: string
}

/** Trạng thái rỗng: nói rõ chỗ này sẽ có gì và (nếu có) bước tiếp theo nên làm. */
export function EmptyState({ icon, title, description, action, size = 'default', className }: EmptyStateProps) {
  const compact = size === 'compact'
  return (
    <div
      className={cn(
        'flex flex-col items-center text-center',
        compact ? 'gap-1.5 px-2 py-3' : 'gap-3 rounded-card border-[1.5px] border-dashed border-line px-6 py-10',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn(
          'grid place-items-center border-[1.5px] border-ink bg-sun',
          compact ? 'size-9 rounded-xl [&_svg]:size-4' : 'size-14 rounded-2xl [&_svg]:size-6',
        )}
      >
        {icon}
      </span>
      <p className={cn('font-medium', compact ? 'text-sm' : 'text-lg')}>{title}</p>
      {description && (
        <p className={cn('max-w-sm leading-relaxed text-muted', compact ? 'text-[0.8125rem]' : 'text-sm')}>
          {description}
        </p>
      )}
      {action && <div className="mt-1">{action}</div>}
    </div>
  )
}
