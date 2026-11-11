import { BookOpen } from 'lucide-react'
import { tagSwatch } from '@/features/library/lib/tagColors'
import { cn } from '@/shared/lib/cn'
import { workspaceIcons } from '../lib/icons'
import type { WorkspaceColor, WorkspaceIcon } from '../types'

const sizes = {
  sm: 'size-7 rounded-lg [&_svg]:size-4',
  md: 'size-11 rounded-xl [&_svg]:size-5',
  lg: 'size-14 rounded-2xl [&_svg]:size-7',
}

/** Ô vuông màu mang biểu tượng của không gian học. Chỉ để nhận ra nhanh; tên luôn đi kèm bên cạnh. */
export function WorkspaceGlyph({
  icon,
  color,
  size = 'md',
  className,
}: {
  icon: WorkspaceIcon
  color: WorkspaceColor
  size?: keyof typeof sizes
  className?: string
}) {
  const Icon = workspaceIcons[icon] ?? BookOpen
  return (
    <span
      aria-hidden="true"
      data-testid="workspace-glyph"
      data-icon={icon}
      data-color={color}
      className={cn(
        'grid shrink-0 place-items-center border-[1.5px] border-ink',
        sizes[size],
        tagSwatch[color] ?? tagSwatch.SUN,
        className,
      )}
    >
      <Icon />
    </span>
  )
}
