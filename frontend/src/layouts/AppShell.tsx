import { useQueryClient } from '@tanstack/react-query'
import { LayoutGrid, LogOut, MonitorSmartphone } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation } from 'react-router'
import { routes } from '@/app/routes'
import { useAuth, useLogout } from '@/features/auth'
import { queryKeys } from '@/shared/api/queryKeys'
import { cn } from '@/shared/lib/cn'
import { Brand, Button } from '@/shared/ui'

const navItems = [
  { to: routes.home, icon: LayoutGrid, label: 'home.nav', end: true },
  { to: routes.devices, icon: MonitorSmartphone, label: 'devices.nav', end: false },
] as const

/**
 * Khung ứng dụng sau đăng nhập theo mẫu: viền tối chứa thanh biểu tượng bên trái, vùng nội dung sáng bo góc.
 * Trên điện thoại thanh biểu tượng chuyển thành hàng tab dưới tiêu đề.
 */
export function AppShell() {
  const { t } = useTranslation()
  const { account } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()
  // Tải lại hẳn trang đăng nhập: xóa sạch mọi dữ liệu của người dùng cũ còn trong bộ nhớ trình duyệt.
  const logout = useLogout(() => window.location.replace(`${routes.login}?loggedOut=1`))
  const handleLogout = () => logout.mutate()

  // Mỗi lần chuyển trang thì hỏi lại máy chủ: phiên hết hạn hoặc bị đăng xuất từ xa sẽ được phát hiện ngay.
  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.session })
  }, [location.pathname, queryClient])

  return (
    <div className="min-h-dvh bg-paper sm:p-5 lg:p-8">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-ink focus:px-4 focus:py-2 focus:text-white"
      >
        {t('app.skipToContent')}
      </a>
      <div className="mx-auto flex min-h-dvh max-w-[1240px] bg-night sm:min-h-[calc(100dvh-2.5rem)] sm:rounded-[2rem] sm:p-3 lg:min-h-[calc(100dvh-4rem)]">
        <nav aria-label={t('app.name')} className="hidden w-20 shrink-0 flex-col items-center gap-3 py-6 sm:flex">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              aria-label={t(item.label)}
              title={t(item.label)}
              className={({ isActive }) =>
                cn(
                  'grid size-12 place-items-center rounded-2xl text-white/80 transition-colors hover:bg-white/10',
                  isActive && 'bg-sun text-ink hover:bg-sun',
                )
              }
            >
              <item.icon className="size-5" aria-hidden="true" />
            </NavLink>
          ))}
          <button
            type="button"
            onClick={handleLogout}
            disabled={logout.isPending}
            aria-label={t('home.logout')}
            title={t('home.logout')}
            className="mt-auto grid size-12 place-items-center rounded-2xl text-white/80 transition-colors hover:bg-white/10"
          >
            <LogOut className="size-5" aria-hidden="true" />
          </button>
        </nav>

        <div className="flex min-w-0 flex-1 flex-col bg-surface px-5 py-6 sm:rounded-[1.5rem] sm:px-8 lg:px-10">
          <header className="flex flex-wrap items-center justify-between gap-4">
            <Brand />
            {account && (
              <div className="flex w-full min-w-0 items-center gap-3 sm:w-auto">
                <span
                  aria-hidden="true"
                  className="grid size-11 place-items-center rounded-full border-[1.5px] border-ink bg-sun text-base font-semibold"
                >
                  {initials(account.displayName)}
                </span>
                <span className="flex min-w-0 flex-1 flex-col leading-tight">
                  <span className="font-semibold" data-testid="account-name">
                    {account.displayName}
                  </span>
                  <span className="truncate text-sm text-muted">{account.email}</span>
                </span>
                <Button
                  variant="outline"
                  className="ml-2 shrink-0 whitespace-nowrap max-sm:size-11 max-sm:px-0"
                  onClick={handleLogout}
                  loading={logout.isPending}
                  aria-label={t('home.logout')}
                  icon={<LogOut className="size-4 sm:hidden" aria-hidden="true" />}
                >
                  <span className="max-sm:sr-only">{logout.isPending ? t('home.loggingOut') : t('home.logout')}</span>
                </Button>
              </div>
            )}
          </header>
          <nav aria-label={t('app.name')} className="mt-5 flex gap-2 sm:hidden">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'inline-flex items-center gap-2 rounded-xl border-[1.5px] border-ink px-3.5 py-2 text-sm font-medium',
                    isActive ? 'bg-ink text-white' : 'bg-surface text-ink',
                  )
                }
              >
                <item.icon className="size-4" aria-hidden="true" />
                {t(item.label)}
              </NavLink>
            ))}
          </nav>
          <main id="main" className="flex-1 pt-8">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/)
  const letters = parts.length > 1 ? parts[0][0] + parts[parts.length - 1][0] : parts[0].slice(0, 2)
  return letters.toUpperCase()
}
