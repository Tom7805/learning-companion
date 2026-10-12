import { useQueryClient } from '@tanstack/react-query'
import { BookOpen, LayoutGrid, LogOut, MonitorSmartphone, ShieldCheck } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { TermsUpdateDialog, useAuth, useLogout } from '@/features/auth'
import { usePageViewTracking, usePrivacyStatus } from '@/features/privacy'
import { SettingsDialog, useProfile } from '@/features/settings'
import { queryKeys } from '@/shared/api/queryKeys'
import { cn } from '@/shared/lib/cn'
import { setDisplayTimeZone } from '@/shared/lib/timezone'
import { Brand, Button } from '@/shared/ui'
import { AccountMenu } from './AccountMenu'

const navItems = [
  { to: routes.home, icon: LayoutGrid, label: 'home.nav', end: true },
  { to: routes.library, icon: BookOpen, label: 'library.nav', end: false },
  { to: routes.devices, icon: MonitorSmartphone, label: 'devices.nav', end: false },
  { to: routes.security, icon: ShieldCheck, label: 'security.nav', end: false },
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
  const profile = useProfile()
  // Điều khoản hoặc chính sách có bản mới chưa đồng ý thì chưa cho dùng tiếp (NCL-01-CN-006).
  const privacy = usePrivacyStatus(account?.id)
  const acceptanceRequired = privacy.data?.acceptanceRequired === true
  usePageViewTracking(
    privacy.data?.purposes.some((item) => item.purpose === 'ANONYMOUS_USAGE_ANALYTICS' && item.granted) === true,
  )
  const timeZone = profile.data?.timeZone
  // Cài đặt mở bằng tham số trên địa chỉ nên tải lại trang hay bấm Quay lại vẫn đúng trạng thái.
  const [params, setParams] = useSearchParams()
  const settingsOpen = params.has('settings')
  const openSettings = () => setParams((current) => ({ ...Object.fromEntries(current), settings: 'profile' }))
  const closeSettings = () =>
    setParams(
      (current) => {
        current.delete('settings')
        return current
      },
      { replace: true },
    )

  // Mọi mốc thời gian hiển thị theo múi giờ trong hồ sơ; chưa chọn thì theo thiết bị (QTN-09).
  useEffect(() => {
    setDisplayTimeZone(timeZone ?? undefined)
  }, [timeZone])

  // Mỗi lần chuyển trang thì hỏi lại máy chủ: phiên hết hạn, bị đăng xuất từ xa hay điều khoản vừa có
  // phiên bản mới đều được phát hiện ngay.
  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.session })
    void queryClient.invalidateQueries({ queryKey: ['privacy'] })
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
          {account && (
            <div className="mt-auto">
              <AccountMenu
                placement="sidebar"
                name={account.displayName}
                email={account.email}
                avatarUrl={profile.data?.avatarUrl}
                onSettings={openSettings}
                onLogout={handleLogout}
                loggingOut={logout.isPending}
              />
            </div>
          )}
        </nav>

        <div className="flex min-w-0 flex-1 flex-col bg-surface px-5 py-6 sm:rounded-[1.5rem] sm:px-8 lg:px-10">
          <header className="flex flex-wrap items-center justify-between gap-4">
            <Brand />
            {account && (
              <div className="flex w-full min-w-0 items-center gap-3 sm:w-auto">
                <AccountMenu
                  placement="header"
                  name={account.displayName}
                  email={account.email}
                  avatarUrl={profile.data?.avatarUrl}
                  onSettings={openSettings}
                  onLogout={handleLogout}
                  loggingOut={logout.isPending}
                />
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
          <nav aria-label={t('app.name')} className="mt-5 flex flex-wrap gap-2 sm:hidden">
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
            {acceptanceRequired ? (
              <p className="text-muted" data-testid="terms-pending">
                {t('termsUpdate.waiting')}
              </p>
            ) : (
              <Outlet />
            )}
          </main>
          {acceptanceRequired && privacy.data && (
            <TermsUpdateDialog status={privacy.data} onLogout={handleLogout} loggingOut={logout.isPending} />
          )}
          <SettingsDialog open={settingsOpen} onClose={closeSettings} />
        </div>
      </div>
    </div>
  )
}
