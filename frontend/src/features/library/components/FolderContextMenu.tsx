import { MoreHorizontal } from 'lucide-react'
import { useEffect, useId, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui'

export interface MenuAction {
  id: string
  label: string
  icon: ReactNode
  danger?: boolean
  onSelect: () => void
}

/** Vị trí của trình đơn nổi dưới nút (gần đáy màn hình thì phía trên); nút đã ra khỏi màn hình thì không có. */
function placement(button: HTMLElement | null, count: number): CSSProperties | null {
  if (!button) return null
  const rect = button.getBoundingClientRect()
  if (rect.bottom < 0 || rect.top > window.innerHeight) return null
  const right = Math.max(8, window.innerWidth - rect.right)
  return window.innerHeight - rect.bottom < 48 * count + 24
    ? { position: 'fixed', bottom: window.innerHeight - rect.top + 4, right }
    : { position: 'fixed', top: rect.bottom + 4, right }
}

/**
 * Trình đơn thao tác nhỏ theo mẫu trình đơn của ARIA: mũi tên lên xuống để di chuyển, Esc đóng và trả tiêu điểm về
 * nút mở, bấm ra ngoài thì đóng. {@code floating}: trình đơn nổi theo vị trí nút trên màn hình, để không bị cắt khi nút nằm
 * trong vùng cuộn (thanh bên); cuộn hay đổi cỡ cửa sổ thì trình đơn đi theo nút, nút ra khỏi màn hình thì đóng.
 */
export function FolderContextMenu({
  label,
  actions,
  className,
  tone,
  floating = false,
}: {
  label: string
  actions: MenuAction[]
  className?: string
  tone?: 'ghost' | 'night'
  floating?: boolean
}) {
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<CSSProperties>()
  const menuId = useId()
  const button = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (!open) return
    menu.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus({ preventScroll: floating })
    const close = (event: PointerEvent) => {
      if (!menu.current?.contains(event.target as Node) && !button.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', close)
    const follow = () => {
      const next = placement(button.current, actions.length)
      if (next) setPosition(next)
      else setOpen(false)
    }
    if (floating) {
      window.addEventListener('scroll', follow, true)
      window.addEventListener('resize', follow)
    }
    return () => {
      document.removeEventListener('pointerdown', close)
      window.removeEventListener('scroll', follow, true)
      window.removeEventListener('resize', follow)
    }
  }, [actions.length, floating, open])

  const toggle = () => {
    if (!open && floating) setPosition(placement(button.current, actions.length) ?? undefined)
    setOpen((value) => !value)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    const items = Array.from(menu.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])
    const index = items.indexOf(document.activeElement as HTMLButtonElement)
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      setOpen(false)
      button.current?.focus()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const next = (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
      items[next]?.focus({ preventScroll: floating })
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      items[event.key === 'Home' ? 0 : items.length - 1]?.focus({ preventScroll: floating })
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
  }

  return (
    <div className={cn('relative', className)}>
      <IconButton
        ref={button}
        label={label}
        icon={<MoreHorizontal className="size-4" />}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={toggle}
        tone={tone}
        className="size-9"
      />
      {open && (
        <ul
          ref={menu}
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={onKeyDown}
          style={floating ? position : undefined}
          className={cn(
            floating ? 'z-50' : 'absolute right-0 top-full z-30 mt-1',
            'flex min-w-52 flex-col gap-0.5 rounded-field border-[1.5px] border-ink bg-surface p-1.5 shadow-pop',
          )}
        >
          {actions.map((action) => (
            <li key={action.id} role="none">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false)
                  action.onSelect()
                }}
                className={cn(
                  'flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm',
                  'hover:bg-ink/5 focus-visible:bg-ink/5 focus-visible:outline-none',
                  action.danger ? 'text-danger' : 'text-ink',
                )}
              >
                {action.icon}
                {action.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
