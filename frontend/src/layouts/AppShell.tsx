import { useQueryClient } from '@tanstack/react-query'
import { Menu, Search, WifiOff } from 'lucide-react'
import { lazy, Suspense, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { SidePanelProvider } from '@/app/providers/SidePanelProvider'
import { installPrompt, useInstallState } from '@/app/pwa'
import { routes } from '@/app/routes'
import { recentStore } from '@/app/store/recentStore'
import { useUiPreferences } from '@/app/store/uiStore'
import { TermsUpdateDialog, useAuth, useLogout } from '@/features/auth'
import { usePageViewTracking, usePrivacyStatus } from '@/features/privacy'
import { useProfile, type SettingsSection } from '@/features/settings'
import { useUiPreferencesSync, useUpdateUiPreferences } from '@/features/settings/api/queries'
import { queryKeys } from '@/shared/api/queryKeys'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { useOnlineStatus } from '@/shared/hooks/useOnlineStatus'
import { cn } from '@/shared/lib/cn'
import { reloadTo } from '@/shared/lib/navigation'
import { setDisplayTimeZone } from '@/shared/lib/timezone'
import { AppMark, Drawer, IconButton, Skeleton, toast } from '@/shared/ui'
import { AccountMenu } from './AccountMenu'
import { HelpPanel } from './HelpPanel'
import { QuickOpen } from './QuickOpen'
import { SidePanel } from './SidePanel'
import { Sidebar, type SidebarAccountProps } from './Sidebar'

const SETTINGS_SECTIONS: SettingsSection[] = ['profile', 'accounts', 'appearance']

// Cài đặt chỉ tải mã khi mở lần đầu, để phần khung mở nhanh hơn.
const SettingsDialog = lazy(async () => ({
  default: (await import('@/features/settings/pages/SettingsDialog')).SettingsDialog,
}))

/**
 * Khung ứng dụng sau đăng nhập (NCL-02-CN-001): thanh bên trái thu gọn được, vùng làm việc chính ở giữa và khung xem
 * bên phải. Trên điện thoại, thanh bên thành ngăn kéo trượt từ cạnh trái và khung xem bên mở toàn màn hình.
 */
export function AppShell() {
  return (
    <SidePanelProvider>
      <AppShellFrame />
    </SidePanelProvider>
  )
}

function AppShellFrame() {
  const { t } = useTranslation()
  const { account } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()
  const panel = useSidePanel()
  const preferences = useUiPreferences()
  const updatePreferences = useUpdateUiPreferences(account?.id)
  useUiPreferencesSync(account?.id)
  const wide = useMediaQuery('(min-width: 768px)')
  const online = useOnlineStatus()
  const installState = useInstallState()
  // Ngăn kéo trên điện thoại gắn với trang lúc mở: đã sang trang khác (bấm mục điều hướng, Mở nhanh) là tự đóng.
  const [drawerPath, setDrawerPath] = useState<string | null>(null)
  const drawerOpen = drawerPath === location.pathname
  const closeDrawer = () => setDrawerPath(null)
  const [quickOpen, setQuickOpen] = useState(false)
  const drawerId = useId()

  // Tải lại hẳn trang đăng nhập: xóa sạch mọi dữ liệu của người dùng cũ còn trong bộ nhớ trình duyệt.
  const logout = useLogout(() => reloadTo(`${routes.login}?loggedOut=1`))
  const handleLogout = () => {
    if (account) recentStore.clear(account.id)
    logout.mutate()
  }
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
  // Đã mở một lần thì giữ hộp thoại trong trang, để khi đóng trình duyệt trả tiêu điểm về đúng chỗ.
  const [settingsMounted, setSettingsMounted] = useState(settingsOpen)
  if (settingsOpen && !settingsMounted) setSettingsMounted(true)
  const settingsSection: SettingsSection = SETTINGS_SECTIONS.find((section) => section === params.get('settings'))
    ?? 'profile'
  const openSettings = (section: SettingsSection = 'profile') =>
    setParams((current) => ({ ...Object.fromEntries(current), settings: section }))
  const closeSettings = () =>
    setParams(
      (current) => {
        current.delete('settings')
        current.delete('linked')
        return current
      },
      { replace: true },
    )

  // Mọi mốc thời gian hiển thị theo múi giờ trong hồ sơ; chưa chọn thì theo thiết bị (QTN-09).
  useEffect(() => {
    setDisplayTimeZone(timeZone ?? undefined)
  }, [timeZone])

  // Mỗi lần chuyển trang thì hỏi lại máy chủ: phiên hết hạn, bị đăng xuất từ xa hay điều khoản vừa có phiên bản
  // mới đều được phát hiện ngay.
  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.session })
    void queryClient.invalidateQueries({ queryKey: ['privacy'] })
  }, [location.pathname, queryClient])

  // Ctrl K (⌘K trên máy Apple) mở nhanh từ bất cứ đâu trong ứng dụng.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setQuickOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  // Có mạng trở lại: báo và tải lại những gì đang hiện.
  const wasOnline = useRef(online)
  useEffect(() => {
    if (!wasOnline.current && online) {
      toast.success(t('shell.reconnected'))
      void queryClient.invalidateQueries()
    }
    wasOnline.current = online
  }, [online, queryClient, t])

  if (!account) return null

  const openHelp = () =>
    panel.open({
      id: 'help',
      title: t('help.title'),
      render: () => <HelpPanel onOpenAppearance={() => openSettings('appearance')} />,
    })
  const install = async () => {
    if ((await installPrompt.install()) === 'accepted') toast.success(t('shell.installed'))
  }
  const menu: SidebarAccountProps = {
    accountId: account.id,
    name: account.displayName,
    email: account.email,
    avatarUrl: profile.data?.avatarUrl,
    onSettings: () => openSettings(),
    onAppearance: () => openSettings('appearance'),
    onHelp: openHelp,
    onInstall: installState === 'available' ? () => void install() : undefined,
    onLogout: handleLogout,
    loggingOut: logout.isPending,
  }
  const collapsed = preferences.sidebarCollapsed
  const toggleCollapsed = () => updatePreferences.mutate({ sidebarCollapsed: !collapsed })

  return (
    <div className="flex min-h-dvh bg-night md:h-dvh md:overflow-hidden" data-testid="app-shell">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-sun focus:px-4 focus:py-2 focus:text-ink"
      >
        {t('app.skipToContent')}
      </a>

      {wide && (
        <div
          data-testid="sidebar"
          data-collapsed={collapsed || undefined}
          className={cn('shrink-0 transition-[width] duration-200 ease-out', collapsed ? 'w-[4.75rem]' : 'w-[18rem]')}
        >
          <Sidebar
            variant={collapsed ? 'collapsed' : 'expanded'}
            onToggleCollapsed={toggleCollapsed}
            onQuickOpen={() => setQuickOpen(true)}
            {...menu}
          />
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col md:flex-row md:gap-2 md:py-2 md:pr-2">
        {!wide && (
          <header className="sticky top-0 z-30 flex items-center gap-1 bg-night px-2 py-2 text-on-night">
            <IconButton
              tone="night"
              label={t('shell.openMenu')}
              aria-expanded={drawerOpen}
              aria-controls={drawerId}
              data-testid="open-sidebar"
              icon={<Menu className="size-5" />}
              onClick={() => setDrawerPath(location.pathname)}
            />
            <Link to={routes.home} className="flex min-w-0 flex-1 items-center gap-2 rounded-xl px-1 outline-none focus-visible:ring-4 focus-visible:ring-sun/60">
              <AppMark className="size-8" />
              <span className="truncate text-[0.9375rem] font-semibold tracking-tight">{t('app.name')}</span>
            </Link>
            <IconButton
              tone="night"
              label={t('shell.quickOpen')}
              icon={<Search className="size-5" />}
              onClick={() => setQuickOpen(true)}
            />
            <AccountMenu placement="topbar" {...menu} />
          </header>
        )}

        <div className="flex min-w-0 flex-1 flex-col rounded-t-[1.25rem] bg-surface text-ink md:overflow-hidden md:rounded-[1.5rem]">
          {!online && (
            <p
              role="status"
              data-testid="offline-banner"
              className="flex items-center gap-2 border-b border-line bg-sun-soft px-5 py-2.5 text-sm font-medium sm:px-8"
            >
              <WifiOff className="size-4 shrink-0" aria-hidden="true" />
              {t('shell.offline')}
            </p>
          )}
          <main id="main" tabIndex={-1} className="flex-1 outline-none md:overflow-y-auto">
            <div className="mx-auto w-full max-w-6xl px-5 py-6 sm:px-8 lg:px-10 lg:py-8">
              {acceptanceRequired ? (
                <p className="text-muted" data-testid="terms-pending">
                  {t('termsUpdate.waiting')}
                </p>
              ) : (
                <Suspense fallback={<PageSkeleton />}>
                  <Outlet />
                </Suspense>
              )}
            </div>
          </main>
        </div>

        <SidePanel />
      </div>

      {!wide && (
        <Drawer id={drawerId} open={drawerOpen} onClose={closeDrawer} label={t('shell.drawerLabel')}>
          <Sidebar
            variant="drawer"
            onClose={closeDrawer}
            onNavigate={closeDrawer}
            onQuickOpen={() => {
              closeDrawer()
              setQuickOpen(true)
            }}
            {...menu}
          />
        </Drawer>
      )}
      <QuickOpen open={quickOpen} onClose={() => setQuickOpen(false)} />
      {acceptanceRequired && privacy.data && (
        <TermsUpdateDialog status={privacy.data} onLogout={handleLogout} loggingOut={logout.isPending} />
      )}
      {settingsMounted && (
        <Suspense fallback={null}>
          <SettingsDialog
            open={settingsOpen}
            section={settingsSection}
            onSection={(section) => openSettings(section)}
            onClose={closeSettings}
          />
        </Suspense>
      )}
    </div>
  )
}

/** Giữ chỗ trong lúc tải mã của trang vừa mở. */
function PageSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true" data-testid="page-skeleton">
      <Skeleton className="h-9 w-2/3 max-w-md" />
      <Skeleton className="h-40" />
    </div>
  )
}
