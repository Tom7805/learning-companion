import { LogOut, Settings } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui'

interface AccountMenuProps {
  name: string
  email: string
  avatarUrl?: string | null
  /** "sidebar": nút ở đáy thanh bên, trình đơn mở lên trên; "header": nút ở đầu trang, mở xuống dưới. */
  placement: 'sidebar' | 'header'
  onSettings: () => void
  onLogout: () => void
  loggingOut?: boolean
}

/**
 * Trình đơn tài khoản: Cài đặt và Đăng xuất. Mở bằng chuột hoặc bàn phím, phím mũi tên chuyển mục,
 * Esc hoặc bấm ra ngoài thì đóng và trả tiêu điểm về nút mở.
 */
export function AccountMenu({ name, email, avatarUrl, placement, onSettings, onLogout, loggingOut }: AccountMenuProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const items = useRef<(HTMLButtonElement | null)[]>([])
  const menuId = useId()

  useEffect(() => {
    if (!open) return undefined
    items.current[0]?.focus()
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [open])

  const close = () => {
    setOpen(false)
    trigger.current?.focus()
  }

  const onMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const list = items.current.filter((item): item is HTMLButtonElement => item !== null)
    const index = list.indexOf(document.activeElement as HTMLButtonElement)
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      list[(index + 1) % list.length]?.focus()
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      list[(index - 1 + list.length) % list.length]?.focus()
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
  }

  const choose = (action: () => void) => {
    setOpen(false)
    action()
  }

  return (
    <div ref={root} className="relative">
      <button
        ref={trigger}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={t('account.menu', { name })}
        title={placement === 'sidebar' ? t('account.menu', { name }) : undefined}
        data-testid={`account-menu-${placement}`}
        onClick={() => setOpen((shown) => !shown)}
        className={cn(
          'grid place-items-center rounded-full outline-none transition-transform focus-visible:ring-4',
          placement === 'sidebar' ? 'focus-visible:ring-sun/60' : 'focus-visible:ring-focus/30',
          'hover:scale-105',
        )}
      >
        <Avatar name={name} src={avatarUrl} size={placement === 'sidebar' ? 'sm' : 'md'} />
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label={t('account.menuLabel')}
          onKeyDown={onMenuKeyDown}
          className={cn(
            'absolute z-50 w-64 rounded-card border-[1.5px] border-ink bg-surface p-2 text-ink shadow-pop animate-fade-up',
            placement === 'sidebar' ? 'bottom-0 left-full ml-3' : 'left-0 top-full mt-2 sm:left-auto sm:right-0',
          )}
        >
          <div className="border-b border-line px-3 pb-2.5 pt-1.5">
            <p className="truncate font-semibold">{name}</p>
            <p className="truncate text-sm text-muted">{email}</p>
          </div>
          <button
            ref={(element) => {
              items.current[0] = element
            }}
            type="button"
            role="menuitem"
            onClick={() => choose(onSettings)}
            className="mt-1 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium outline-none hover:bg-ink/5 focus-visible:bg-ink/5"
          >
            <Settings className="size-4" aria-hidden="true" />
            {t('settings.title')}
          </button>
          <button
            ref={(element) => {
              items.current[1] = element
            }}
            type="button"
            role="menuitem"
            disabled={loggingOut}
            onClick={() => choose(onLogout)}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium text-danger outline-none hover:bg-danger-soft focus-visible:bg-danger-soft"
          >
            <LogOut className="size-4" aria-hidden="true" />
            {t('home.logout')}
          </button>
        </div>
      )}
    </div>
  )
}
