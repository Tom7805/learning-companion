import { ChevronsUpDown, Download, LifeBuoy, LogOut, Palette, Settings } from 'lucide-react'
import { Fragment, useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui'

interface AccountMenuProps {
  name: string
  email: string
  avatarUrl?: string | null
  /**
   * "sidebar": nút đầy đủ (ảnh, tên, địa chỉ thư) ở đáy thanh bên, trình đơn mở lên trên;
   * "sidebar-collapsed": chỉ ảnh đại diện ở thanh bên thu gọn, trình đơn mở sang phải;
   * "topbar": ảnh đại diện ở thanh trên cùng của điện thoại, trình đơn mở xuống dưới.
   */
  placement: 'sidebar' | 'sidebar-collapsed' | 'topbar'
  onSettings: () => void
  onAppearance: () => void
  onHelp: () => void
  /** Chỉ có khi trình duyệt cho cài ứng dụng. */
  onInstall?: () => void
  onLogout: () => void
  loggingOut?: boolean
}

interface MenuItem {
  key: string
  label: string
  icon: typeof Settings
  action: () => void
  danger?: boolean
  disabled?: boolean
}

/**
 * Trình đơn tài khoản ở đáy thanh bên (NCL-02-CN-001): cài đặt, giao diện, trợ giúp, cài ứng dụng và đăng xuất.
 * Mở bằng chuột hoặc bàn phím, phím mũi tên chuyển mục, Esc hoặc bấm ra ngoài thì đóng và trả tiêu điểm về nút mở.
 */
export function AccountMenu({
  name,
  email,
  avatarUrl,
  placement,
  onSettings,
  onAppearance,
  onHelp,
  onInstall,
  onLogout,
  loggingOut,
}: AccountMenuProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([])
  const menuId = useId()

  const items: MenuItem[] = [
    { key: 'settings', label: t('settings.title'), icon: Settings, action: onSettings },
    { key: 'appearance', label: t('appearance.nav'), icon: Palette, action: onAppearance },
    { key: 'help', label: t('help.title'), icon: LifeBuoy, action: onHelp },
    ...(onInstall ? [{ key: 'install', label: t('shell.install'), icon: Download, action: onInstall }] : []),
    { key: 'logout', label: t('home.logout'), icon: LogOut, action: onLogout, danger: true, disabled: loggingOut },
  ]

  useEffect(() => {
    if (!open) return undefined
    itemRefs.current[0]?.focus()
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
    const list = itemRefs.current.filter((item): item is HTMLButtonElement => item !== null)
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
    } else if (event.key === 'Home') {
      event.preventDefault()
      list[0]?.focus()
    } else if (event.key === 'End') {
      event.preventDefault()
      list[list.length - 1]?.focus()
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
  }

  const choose = (action: () => void) => {
    setOpen(false)
    action()
  }

  const full = placement === 'sidebar'
  return (
    <div ref={root} className={cn('relative', full && 'w-full')}>
      <button
        ref={trigger}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={t('account.menu', { name })}
        data-testid={`account-menu-${placement === 'topbar' ? 'topbar' : 'sidebar'}`}
        onClick={() => setOpen((shown) => !shown)}
        className={cn(
          'flex items-center gap-3 rounded-2xl text-left outline-none transition-colors',
          'focus-visible:ring-4 focus-visible:ring-sun/60',
          full ? 'w-full p-2 text-on-night hover:bg-on-night/10' : 'rounded-full p-0.5 hover:scale-105',
        )}
      >
        <Avatar name={name} src={avatarUrl} size="sm" />
        {full && (
          <>
            <span className="flex min-w-0 flex-1 flex-col leading-tight">
              <span className="truncate text-sm font-semibold" data-testid="account-name">
                {name}
              </span>
              <span className="truncate text-xs text-on-night/70">{email}</span>
            </span>
            <ChevronsUpDown className="size-4 shrink-0 text-on-night/70" aria-hidden="true" />
          </>
        )}
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label={t('account.menuLabel')}
          onKeyDown={onMenuKeyDown}
          className={cn(
            'absolute z-50 rounded-card border-[1.5px] border-ink bg-surface p-2 text-ink shadow-pop animate-fade-up',
            placement === 'sidebar' && 'bottom-full left-0 mb-2 w-full min-w-60',
            placement === 'sidebar-collapsed' && 'bottom-0 left-full ml-3 w-64',
            placement === 'topbar' && 'right-0 top-full mt-2 w-64',
          )}
        >
          <div className="border-b border-line px-3 pb-2.5 pt-1.5">
            <p className="truncate font-semibold">{name}</p>
            <p className="truncate text-sm text-muted">{email}</p>
          </div>
          <div className="mt-1 flex flex-col">
            {items.map((item, index) => (
              <Fragment key={item.key}>
                {item.danger && <div role="separator" className="my-1 border-t border-line" />}
                <button
                  ref={(element) => {
                    itemRefs.current[index] = element
                  }}
                  type="button"
                  role="menuitem"
                  disabled={item.disabled}
                  onClick={() => choose(item.action)}
                  className={cn(
                    'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium outline-none',
                    item.danger
                      ? 'text-danger hover:bg-danger-soft focus-visible:bg-danger-soft'
                      : 'hover:bg-ink/5 focus-visible:bg-ink/5',
                  )}
                >
                  <item.icon className="size-4" aria-hidden="true" />
                  {item.label}
                </button>
              </Fragment>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
