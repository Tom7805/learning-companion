import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { workspacePath } from '@/app/routes'
import type { Workspace } from '../types'
import { WorkspaceGlyph } from './WorkspaceGlyph'

/** Thẻ một không gian học trong danh sách: biểu tượng, tên, mô tả và số tài liệu đang gắn. */
export function WorkspaceCard({ workspace }: { workspace: Workspace }) {
  const { t, i18n } = useTranslation()
  return (
    <Link
      to={workspacePath(workspace.id)}
      data-testid="workspace-card"
      data-name={workspace.name}
      className="flex h-full flex-col gap-3 rounded-card border-[1.5px] border-ink bg-surface p-4 transition-transform hover:-translate-y-0.5 hover:shadow-pop"
    >
      <div className="flex items-start gap-3">
        <WorkspaceGlyph icon={workspace.icon} color={workspace.color} />
        <h2 className="min-w-0 flex-1 pt-1.5 text-lg font-medium leading-snug [overflow-wrap:anywhere]">
          {workspace.name}
        </h2>
      </div>
      {workspace.description && (
        <p className="line-clamp-2 text-sm leading-relaxed text-ink-soft [overflow-wrap:anywhere]">
          {workspace.description}
        </p>
      )}
      <p className="mt-auto text-[0.8125rem] font-medium tabular-nums text-muted" data-testid="workspace-card-count">
        {workspace.documentCount > 0
          ? t('workspaces.documentCount', {
              count: workspace.documentCount,
              formatted: workspace.documentCount.toLocaleString(i18n.language),
            })
          : t('workspaces.noDocuments')}
      </p>
    </Link>
  )
}
