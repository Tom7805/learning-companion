import { Plus } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink } from 'react-router'
import { routes, workspacePath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { useWorkspaces } from '../api/queries'
import { WorkspaceGlyph } from './WorkspaceGlyph'

/** Số không gian học hiện ngay trên thanh bên; còn lại nằm sau đường dẫn "Xem tất cả". */
const SHOWN = 5

/**
 * Khu không gian học trên thanh bên (NCL-06-CN-001): các không gian học vừa dùng gần nhất, nút tạo mới và đường dẫn tới
 * danh sách đầy đủ. Người học có bao nhiêu không gian học thanh bên cũng chỉ hiện vài dòng.
 */
export function SidebarWorkspaces({ onNavigate }: { onNavigate?: () => void }) {
  const { t, i18n } = useTranslation()
  // Khung ứng dụng đã nạp danh sách; mở ngăn kéo trên điện thoại không gửi thêm yêu cầu.
  const workspaces = useWorkspaces(true, false)
  const headingId = useId()
  const list = workspaces.data ?? []

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-1.5" data-testid="sidebar-workspaces">
      <div className="flex items-center justify-between gap-2 pl-3 pr-1">
        <h2 id={headingId} className="text-xs font-semibold uppercase tracking-wide text-on-night/70">
          {t('workspaces.sidebar.title')}
        </h2>
        <Link
          to={`${routes.workspaces}?new=1`}
          onClick={onNavigate}
          aria-label={t('workspaces.create')}
          title={t('workspaces.create')}
          data-testid="sidebar-new-workspace"
          className="grid size-8 place-items-center rounded-lg text-on-night/80 outline-none hover:bg-on-night/10 hover:text-on-night focus-visible:ring-4 focus-visible:ring-sun/60"
        >
          <Plus className="size-4" aria-hidden="true" />
        </Link>
      </div>
      {workspaces.isSuccess && list.length === 0 && (
        <p className="px-3 text-[0.8125rem] leading-snug text-on-night/70">{t('workspaces.sidebar.empty')}</p>
      )}
      {list.length > 0 && (
        <ul className="flex flex-col gap-0.5">
          {list.slice(0, SHOWN).map((workspace) => (
            <li key={workspace.id}>
              <NavLink
                to={workspacePath(workspace.id)}
                onClick={onNavigate}
                data-testid="sidebar-workspace"
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2.5 rounded-lg px-3 py-1.5 text-sm',
                    isActive ? 'bg-on-night/15 text-on-night' : 'text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
                  )
                }
              >
                <WorkspaceGlyph icon={workspace.icon} color={workspace.color} size="sm" />
                <span className="truncate">{workspace.name}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      )}
      {list.length > SHOWN && (
        <Link
          to={routes.workspaces}
          onClick={onNavigate}
          data-testid="sidebar-all-workspaces"
          className="rounded-lg px-3 py-1.5 text-[0.8125rem] text-on-night/80 underline-offset-4 hover:text-on-night hover:underline"
        >
          {t('workspaces.sidebar.all', { count: list.length, formatted: list.length.toLocaleString(i18n.language) })}
        </Link>
      )}
    </section>
  )
}
