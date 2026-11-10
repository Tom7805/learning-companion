import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { workspacePath } from '@/app/routes'
import { Skeleton } from '@/shared/ui'
import type { WorkspaceSummary } from '../types'
import { WorkspaceGlyph } from './WorkspaceGlyph'

/**
 * Danh sách không gian học đang dùng một tài liệu, cho khung thông tin tài liệu trong kho (NCL-06-CN-001): mỗi dòng là
 * đường dẫn mở không gian học đó.
 */
export function UsedInWorkspaces({
  workspaces,
  loading,
  onNavigate,
}: {
  workspaces: WorkspaceSummary[] | undefined
  loading?: boolean
  onNavigate?: () => void
}) {
  const { t } = useTranslation()
  if (loading) return <Skeleton className="h-5 w-32" />
  if (!workspaces?.length) return <span data-testid="document-usage">{t('workspaces.usage.none')}</span>
  return (
    <ul className="flex flex-col gap-1.5" data-testid="document-usage">
      {workspaces.map((workspace) => (
        <li key={workspace.id}>
          <Link
            to={workspacePath(workspace.id)}
            onClick={onNavigate}
            className="inline-flex max-w-full items-center gap-2 rounded-lg py-0.5 underline-offset-4 hover:underline"
          >
            <WorkspaceGlyph icon={workspace.icon} color={workspace.color} size="sm" />
            <span className="[overflow-wrap:anywhere]">{workspace.name}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

/**
 * Lời báo trước khi xóa tài liệu đang được dùng (NCL-06-CN-001 TC-03): nêu tên từng không gian học bị ảnh hưởng, đặt
 * ngay trong hộp thoại xác nhận. Không có không gian học nào dùng thì không hiện gì.
 */
export function DeleteImpactNote({ workspaces }: { workspaces: WorkspaceSummary[] | undefined }) {
  const { t } = useTranslation()
  if (!workspaces?.length) return null
  return (
    <div
      role="alert"
      data-testid="delete-impact"
      className="mt-3 flex flex-col gap-2 rounded-xl border-[1.5px] border-ink bg-warning-soft px-4 py-3 text-ink"
    >
      <p className="font-medium">{t('workspaces.usage.warning', { count: workspaces.length })}</p>
      <ul className="flex flex-col gap-1.5">
        {workspaces.map((workspace) => (
          <li key={workspace.id} className="flex items-center gap-2" data-testid="delete-impact-workspace">
            <WorkspaceGlyph icon={workspace.icon} color={workspace.color} size="sm" />
            <span className="[overflow-wrap:anywhere]">{workspace.name}</span>
          </li>
        ))}
      </ul>
      <p className="text-sm">{t('workspaces.usage.consequence')}</p>
    </div>
  )
}
