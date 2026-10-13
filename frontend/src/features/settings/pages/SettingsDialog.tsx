import { ChevronRight, Lock, MonitorSmartphone, ShieldCheck, UserCog, UserRound, X } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { Alert, Button, Skeleton } from '@/shared/ui'
import { useProfile } from '../api/queries'
import { AccountSection } from '../components/AccountSection'
import { ProfileSection } from '../components/ProfileSection'

export type SettingsSection = 'profile' | 'accounts'

interface SettingsDialogProps {
  open: boolean
  section?: SettingsSection
  onSection?: (section: SettingsSection) => void
  onClose: () => void
}

/**
 * Cài đặt mở từ trình đơn tài khoản (NCL-01-CN-005). Mục Hồ sơ và Tài khoản (liên kết đăng nhập, xuất dữ liệu)
 * nằm ngay trong hộp thoại; Bảo mật và Thiết bị đã có trang riêng nên ở đây là lối tắt sang trang đó.
 */
export function SettingsDialog({ open, section = 'profile', onSection, onClose }: SettingsDialogProps) {
  const { t } = useTranslation()
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  const profile = useProfile()

  useEffect(() => {
    const element = dialog.current
    if (!element) return
    if (open && !element.open) {
      if (typeof element.showModal === 'function') element.showModal()
      else element.setAttribute('open', '')
      heading.current?.focus()
    } else if (!open && element.open) {
      if (typeof element.close === 'function') element.close()
      else element.removeAttribute('open')
    }
  }, [open])

  const sections: { id: SettingsSection; icon: typeof UserRound; label: string }[] = [
    { id: 'profile', icon: UserRound, label: t('profile.nav') },
    { id: 'accounts', icon: UserCog, label: t('accountSettings.nav') },
  ]
  const shortcuts = [
    { to: routes.security, icon: ShieldCheck, label: t('security.nav') },
    { to: routes.devices, icon: MonitorSmartphone, label: t('devices.nav') },
    { to: routes.privacySettings, icon: Lock, label: t('privacy.nav') },
  ]

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      data-testid="settings-dialog"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="m-0 h-dvh max-h-none w-screen max-w-none bg-surface p-0 text-ink backdrop:bg-ink/45 sm:m-auto sm:h-[min(88dvh,760px)] sm:w-[min(94vw,980px)] sm:rounded-card sm:border-[1.5px] sm:border-ink sm:shadow-pop"
    >
      {open && (
        <div className="flex h-full flex-col sm:flex-row">
          <nav
            aria-label={t('settings.title')}
            className="flex shrink-0 flex-col gap-1 border-b border-line bg-paper/60 p-4 sm:w-60 sm:border-b-0 sm:border-r sm:p-5"
          >
            <div className="mb-2 flex items-center justify-between gap-2">
              <h1 ref={heading} id={titleId} tabIndex={-1} className="text-xl font-semibold outline-none">
                {t('settings.title')}
              </h1>
              <Button
                variant="ghost"
                className="size-10 px-0 sm:hidden"
                onClick={onClose}
                aria-label={t('common.close')}
                icon={<X className="size-5" aria-hidden="true" />}
              />
            </div>
            <div className="flex gap-2 overflow-x-auto sm:flex-col sm:gap-1">
              {sections.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  aria-current={section === item.id ? 'page' : undefined}
                  onClick={() => onSection?.(item.id)}
                  className={cn(
                    'inline-flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm font-medium',
                    section === item.id ? 'bg-ink text-white' : 'text-ink-soft hover:bg-ink/5 hover:text-ink',
                  )}
                >
                  <item.icon className="size-4" aria-hidden="true" />
                  {item.label}
                </button>
              ))}
              {shortcuts.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={onClose}
                  className={cn(
                    'inline-flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2.5 text-sm font-medium text-ink-soft',
                    'hover:bg-ink/5 hover:text-ink',
                  )}
                >
                  <item.icon className="size-4" aria-hidden="true" />
                  <span className="flex-1">{item.label}</span>
                  <ChevronRight className="hidden size-4 text-muted sm:block" aria-hidden="true" />
                </Link>
              ))}
            </div>
          </nav>

          <div className="relative min-h-0 flex-1 overflow-y-auto p-5 sm:p-8">
            <Button
              variant="ghost"
              className="absolute right-4 top-4 hidden size-10 px-0 sm:inline-flex"
              onClick={onClose}
              aria-label={t('common.close')}
              icon={<X className="size-5" aria-hidden="true" />}
            />
            {section === 'accounts' && <AccountSection onNavigate={onClose} />}
            {section === 'profile' && profile.isPending && (
              <div className="flex flex-col gap-5" aria-busy="true">
                <Skeleton className="h-8 w-48" />
                <Skeleton className="h-24" />
                <Skeleton className="h-14" />
                <Skeleton className="h-14" />
              </div>
            )}
            {section === 'profile' && profile.isError && (
              <Alert tone="danger">
                <p>{t('profile.loadFailed')}</p>
                <Button variant="outline" className="mt-3" onClick={() => void profile.refetch()}>
                  {t('common.retry')}
                </Button>
              </Alert>
            )}
            {section === 'profile' && profile.data && <ProfileSection profile={profile.data} />}
          </div>
        </div>
      )}
    </dialog>
  )
}
