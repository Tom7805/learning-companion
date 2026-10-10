import { LayoutGrid, LogOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { routes } from '@/app/routes'
import { useAuth, useLogout } from '@/features/auth'
import { cn } from '@/shared/lib/cn'
import { Brand, Button } from '@/shared/ui'

/**
 * Khung ứng dụng sau đăng nhập theo mẫu: viền tối chứa thanh biểu tượng bên trái, vùng nội dung sáng bo góc.
 * Thanh điều hướng đầy đủ thuộc story khung ứng dụng (NCL-02); ở đây chỉ có trang chính và đăng xuất.
 */
export function AppShell() {
  const { t } = useTranslation()
  const { account } = useAuth()
  // Tải lại hẳn trang đăng nhập: xóa sạch mọi dữ liệu của người dùng cũ còn trong bộ nhớ trình duyệt.
  const logout = useLogout(() => window.location.replace(routes.login))

  const handleLogout = () => logout.mutate()

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
          <NavLink
            to={routes.home}
            end
            aria-label={t('home.nav')}
            className={({ isActive }) =>
              cn(
                'grid size-12 place-items-center rounded-2xl text-white/80 transition-colors hover:bg-white/10',
                isActive && 'bg-sun text-ink hover:bg-sun',
              )
            }
          >
            <LayoutGrid className="size-5" aria-hidden="true" />
          </NavLink>
          <button
            type="button"
            onClick={handleLogout}
            disabled={logout.isPending}
            aria-label={t('home.logout')}
            className="mt-auto grid size-12 place-items-center rounded-2xl text-white/80 transition-colors hover:bg-white/10"
          >
            <LogOut className="size-5" aria-hidden="true" />
          </button>
        </nav>

        <div className="flex min-w-0 flex-1 flex-col bg-surface px-5 py-6 sm:rounded-[1.5rem] sm:px-8 lg:px-10">
          <header className="flex flex-wrap items-center justify-between gap-4">
            <Brand />
            {account && (
              <div className="flex items-center gap-3">
                <span
                  aria-hidden="true"
                  className="grid size-11 place-items-center rounded-full border-[1.5px] border-ink bg-sun text-base font-semibold"
                >
                  {initials(account.displayName)}
                </span>
                <span className="flex flex-col leading-tight">
                  <span className="font-semibold" data-testid="account-name">
                    {account.displayName}
                  </span>
                  <span className="text-sm text-muted">{account.email}</span>
                </span>
                <Button variant="outline" className="ml-2" onClick={handleLogout} loading={logout.isPending}>
                  {logout.isPending ? t('home.loggingOut') : t('home.logout')}
                </Button>
              </div>
            )}
          </header>
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
