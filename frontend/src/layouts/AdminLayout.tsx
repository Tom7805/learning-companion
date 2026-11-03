import { ScrollText, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { routes } from '@/app/routes'
import { cn } from '@/shared/lib/cn'

/** Các trang của khu quản trị; story quản trị sau chỉ cần thêm mục vào đây. */
const sections = [{ id: 'audit', to: routes.adminAudit, label: 'admin.nav.audit', icon: ScrollText }]

/** Khung chung của khu quản trị, nằm trong vùng nội dung của ứng dụng: nhãn khu vực và các trang quản trị. */
export function AdminLayout() {
  const { t } = useTranslation()
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
      </div>
      <Outlet />
    </div>
  )
}
