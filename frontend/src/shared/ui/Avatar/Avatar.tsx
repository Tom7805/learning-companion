import { cn } from '@/shared/lib/cn'

interface AvatarProps {
  name: string
  src?: string | null
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}

const sizes = {
  sm: 'size-9 text-sm',
  md: 'size-11 text-base',
  lg: 'size-16 text-xl',
  xl: 'size-24 text-3xl',
} as const

/** Ảnh đại diện tròn viền mực; chưa có ảnh thì hiện chữ cái đầu trên nền vàng như mẫu. */
export function Avatar({ name, src, size = 'md', className }: AvatarProps) {
  const base = cn(
    'grid shrink-0 place-items-center overflow-hidden rounded-full border-[1.5px] border-ink bg-sun font-semibold',
    sizes[size],
    className,
  )
  if (src) {
    return <img src={src} alt="" className={cn(base, 'object-cover')} data-testid="avatar-image" />
  }
  return (
    <span aria-hidden="true" className={base}>
      {initials(name)}
    </span>
  )
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : [...parts[0]].slice(0, 2).join('')
  return letters.toUpperCase()
}
