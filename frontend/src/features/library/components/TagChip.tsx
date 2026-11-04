import type { ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { tagSwatch } from '../lib/tagColors'
import type { Tag } from '../types'


/** Nhãn nhỏ có chấm màu và tên: màu chỉ để nhận ra nhanh, tên luôn hiện để không phụ thuộc vào màu. */
export function TagChip({ tag, className, children }: { tag: Pick<Tag, 'name' | 'color'>; className?: string; children?: ReactNode }) {
  return (
    <span
      data-testid="tag-chip"
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 rounded-lg border-[1.5px] border-ink px-2 py-0.5 text-xs font-medium',
        tagSwatch[tag.color],
        className,
      )}
    >
      <span className="truncate">{tag.name}</span>
      {children}
    </span>
  )
}

/** Chấm màu kèm tên nhãn, cho chỗ đã có nền riêng như nút lọc. */
export function TagDot({ tag }: { tag: Pick<Tag, 'name' | 'color'> }) {
  return (
    <>
      <span aria-hidden="true" className={cn('size-3 shrink-0 rounded-full border-[1.5px] border-ink', tagSwatch[tag.color])} />
      <span className="truncate">{tag.name}</span>
    </>
  )
}
