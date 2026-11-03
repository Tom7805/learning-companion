import { Clock, Cpu, Gauge, ScrollText, ShieldAlert, ShieldCheck, SlidersHorizontal } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { routes } from '@/app/routes'
import { useAdminIdentity } from '@/features/admin'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { cn } from '@/shared/lib/cn'

/** Các trang của khu quản trị; story quản trị sau chỉ cần thêm mục vào đây. */
const sections = [
  { id: 'audit', to: routes.adminAudit, label: 'admin.nav.audit', icon: ScrollText },
  { id: 'ai', to: routes.adminAi, label: 'admin.nav.ai', icon: Cpu },
  { id: 'ai-config', to: routes.adminAiConfig, label: 'admin.nav.aiConfig', icon: SlidersHorizontal },
  { id: 'quotas', to: routes.adminQuotas, label: 'admin.nav.quotas', icon: Gauge },
  { id: 'safety', to: routes.adminSafety, label: 'admin.nav.safety', icon: ShieldAlert },
]

/** Khung chung của khu quản trị, nằm trong vùng nội dung của ứng dụng: nhãn khu vực và các trang quản trị. */
export function AdminLayout() {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const identity = useAdminIdentity().data
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3 border-b border-line pb-4">
        <p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-muted">
          <ShieldCheck className="size-4" aria-hidden="true" />
          {t('admin.area')}
        </p>
        <nav aria-label={t('admin.navLabel')} className="-mx-1 flex min-w-0 gap-1 overflow-x-auto px-1">
          {sections.map((section) => (
            <NavLink
              key={section.id}
              to={section.to}
              className={({ isActive }) =>
                cn(
                  'flex shrink-0 items-center gap-2 rounded-full border-[1.5px] px-3.5 py-1.5 text-sm font-medium',
                  'outline-none transition-colors focus-visible:ring-4 focus-visible:ring-focus/25',
                  isActive ? 'border-ink bg-sun text-ink' : 'border-line text-ink-soft hover:border-ink',
                )
              }
            >
              <section.icon className="size-4" aria-hidden="true" />
              {t(section.label)}
            </NavLink>
          ))}
        </nav>
        {identity?.sessionExpiresAt && (
          // Phiên quản trị có hạn cứng tám giờ (NCL-03-CN-002): báo trước giờ phải đăng nhập lại.
          <p className="flex items-center gap-1.5 text-sm text-muted sm:ml-auto" data-testid="admin-session-expiry">
            <Clock className="size-4" aria-hidden="true" />
            {t('admin.sessionExpiresAt', { time: formatDateTime(identity.sessionExpiresAt, i18n.language, timeZone) })}
          </p>
        )}
      </div>
      <Outlet />
    </div>
  )
}
