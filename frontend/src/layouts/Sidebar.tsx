import { FileText, PanelLeftClose, PanelLeftOpen, Search, X } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink } from 'react-router'
import { navigation } from '@/app/navigation'
import { routes } from '@/app/routes'
import { useRecentItems } from '@/app/store/recentStore'
import { cn } from '@/shared/lib/cn'
import { AppMark, IconButton, Kbd, Tooltip } from '@/shared/ui'
import { AccountMenu } from './AccountMenu'

export interface SidebarAccountProps {
  accountId: string
  name: string
  email: string
  avatarUrl?: string | null
  onSettings: () => void
  onAppearance: () => void
  onHelp: () => void
  onInstall?: () => void
  onLogout: () => void
  loggingOut?: boolean
}

/** Phím tắt mở nhanh (AppShell nghe phím): ⌘K trên máy Apple, Ctrl K trên máy khác. */
const QUICK_OPEN_KEYS = /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘K' : 'Ctrl K'

interface SidebarProps extends SidebarAccountProps {
  /** "drawer": ngăn kéo trên điện thoại, luôn hiện đủ chữ và có nút đóng. */
  variant: 'expanded' | 'collapsed' | 'drawer'
  onToggleCollapsed?: () => void
  onClose?: () => void
  /** Gọi sau khi chọn một mục, để ngăn kéo tự đóng. */
  onNavigate?: () => void
  onQuickOpen: () => void
  /** Chuông thông báo, đặt cạnh nút Mở nhanh ở đầu thanh bên. */
  bell?: ReactNode
}

/**
 * Thanh bên (NCL-02-CN-001): trên cùng là nút hành động chính, tiếp theo là nhóm mục do từng chức năng đăng ký,
 * khu mục gần đây, và trình đơn tài khoản ở đáy. Thu gọn thì còn cột biểu tượng, rê chuột thấy tên mục.
 */
export function Sidebar({
  variant,
  onToggleCollapsed,
  onClose,
  onNavigate,
  onQuickOpen,
  bell,
  ...account
}: SidebarProps) {
  const { t } = useTranslation()
  const collapsed = variant === 'collapsed'
  const recent = useRecentItems(account.accountId)
  const recentId = useId()

  const item = (active: boolean) =>
    cn(
      'flex items-center gap-3 rounded-xl text-sm font-medium transition-colors',
      collapsed ? 'size-11 justify-center' : 'px-3 py-2.5',
      active ? 'bg-sun text-ink' : 'text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
    )

  return (
    <div className={cn('flex h-full flex-col gap-5 bg-night text-on-night', collapsed ? 'items-center px-2 py-3' : 'p-3')}>
      <div className={cn('flex items-center gap-2', collapsed ? 'flex-col' : 'justify-between pl-1')}>
        <Link
          to={routes.home}
          onClick={onNavigate}
          className="flex min-w-0 items-center gap-2.5 rounded-xl outline-none focus-visible:ring-4 focus-visible:ring-sun/60"
          aria-label={collapsed ? t('app.name') : undefined}
        >
          <AppMark />
          {!collapsed && <span className="truncate text-[0.9375rem] font-semibold tracking-tight">{t('app.name')}</span>}
        </Link>
        {variant === 'drawer' ? (
          <IconButton tone="night" label={t('shell.closeMenu')} icon={<X className="size-5" />} onClick={onClose} />
        ) : (
          <Tooltip label={collapsed ? t('shell.expand') : t('shell.collapse')} side={collapsed ? 'right' : 'top'}>
            <IconButton
              tone="night"
              label={collapsed ? t('shell.expand') : t('shell.collapse')}
              aria-expanded={!collapsed}
              data-testid="sidebar-toggle"
              icon={collapsed ? <PanelLeftOpen className="size-5" /> : <PanelLeftClose className="size-5" />}
              onClick={onToggleCollapsed}
            />
          </Tooltip>
        )}
      </div>

      {collapsed ? (
        <div className="flex flex-col items-center gap-2">
          <Tooltip label={t('shell.quickOpen')}>
            <button
              type="button"
              onClick={onQuickOpen}
              aria-label={t('shell.quickOpen')}
              aria-keyshortcuts="Control+K Meta+K"
              className="grid size-11 place-items-center rounded-xl bg-sun text-ink outline-none focus-visible:ring-4 focus-visible:ring-sun/60"
            >
              <Search className="size-5" aria-hidden="true" />
            </button>
          </Tooltip>
          {bell && <Tooltip label={t('notifications.title')}>{bell}</Tooltip>}
        </div>
      ) : (
        <div className="flex w-full items-stretch gap-2">
          <button
            type="button"
            onClick={onQuickOpen}
            aria-keyshortcuts="Control+K Meta+K"
            className={cn(
              'flex min-w-0 flex-1 items-center gap-3 rounded-2xl bg-sun px-3.5 py-3 text-left text-ink outline-none',
              'transition-transform hover:-translate-y-px focus-visible:ring-4 focus-visible:ring-sun/60',
            )}
          >
            <Search className="size-5 shrink-0" aria-hidden="true" />
            <span className="flex min-w-0 flex-1 flex-col gap-0.5 leading-tight">
              <span className="flex items-center justify-between gap-2">
                <span className="font-semibold">{t('shell.quickOpen')}</span>
                {variant === 'expanded' && (
                  <Kbd className="h-5 shrink-0 text-ink-soft">
                    <span aria-hidden="true">{QUICK_OPEN_KEYS}</span>
                  </Kbd>
                )}
              </span>
              <span className="truncate text-xs text-ink-soft">{t('shell.quickOpenHint')}</span>
            </span>
          </button>
          {bell}
        </div>
      )}

      <nav aria-label={t('shell.nav')} className={cn(!collapsed && 'w-full')}>
        <ul className="flex flex-col gap-1">
          {navigation.map((entry) => (
            <li key={entry.id}>
              {collapsed ? (
                <Tooltip label={t(entry.label)}>
                  <NavLink
                    to={entry.to}
                    end={entry.end}
                    aria-label={t(entry.label)}
                    className={({ isActive }) => item(isActive)}
                  >
                    <entry.icon className="size-5" aria-hidden="true" />
                  </NavLink>
                </Tooltip>
              ) : (
                <NavLink
                  to={entry.to}
                  end={entry.end}
                  onClick={onNavigate}
                  className={({ isActive }) => item(isActive)}
                >
                  <entry.icon className="size-5 shrink-0" aria-hidden="true" />
                  {t(entry.label)}
                </NavLink>
              )}
            </li>
          ))}
        </ul>
      </nav>

      {!collapsed && (
        <section aria-labelledby={recentId} className="flex min-h-0 flex-col gap-1.5" data-testid="sidebar-recent">
          <h2 id={recentId} className="px-3 text-xs font-semibold uppercase tracking-wide text-on-night/70">
            {t('shell.recent')}
          </h2>
          {recent.length === 0 ? (
            <p className="px-3 text-[0.8125rem] leading-snug text-on-night/70">{t('shell.recentEmpty')}</p>
          ) : (
            <ul className="flex flex-col gap-0.5 overflow-y-auto">
              {recent.map((entry) => (
                <li key={`${entry.kind}:${entry.id}`}>
                  <NavLink
                    to={entry.to}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm',
                        isActive ? 'bg-on-night/15 text-on-night' : 'text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
                      )
                    }
                  >
                    <FileText className="size-4 shrink-0" aria-hidden="true" />
                    <span className="truncate">{entry.title}</span>
                  </NavLink>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      <div className={cn('mt-auto', !collapsed && 'w-full')}>
        <AccountMenu
          placement={collapsed ? 'sidebar-collapsed' : 'sidebar'}
          name={account.name}
          email={account.email}
          avatarUrl={account.avatarUrl}
          onSettings={account.onSettings}
          onAppearance={account.onAppearance}
          onHelp={account.onHelp}
          onInstall={account.onInstall}
          onLogout={account.onLogout}
          loggingOut={account.loggingOut}
        />
      </div>
    </div>
  )
}
