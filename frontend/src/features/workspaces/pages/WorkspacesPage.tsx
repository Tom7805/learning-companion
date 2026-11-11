import { Layers, Plus, SearchX } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { workspacePath } from '@/app/routes'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { matchesSearch } from '@/shared/lib/vietnamese'
import { Alert, Button, EmptyState, Skeleton, TextField } from '@/shared/ui'
import { useWorkspaces } from '../api/queries'
import { CreateWorkspaceDialog } from '../components/CreateWorkspaceDialog'
import { WorkspaceCard } from '../components/WorkspaceCard'

/** Từ số này trở lên mới hiện ô tìm: ít không gian học thì nhìn là thấy. */
const SEARCH_FROM = 7

/**
 * Danh sách không gian học của người học (NCL-06-CN-001): tạo bao nhiêu cũng được (QTN-14), vừa dùng đứng trước, tìm
 * theo tên không phân biệt dấu. Địa chỉ {@code ?new=1} mở sẵn hộp thoại tạo (nút cộng trên thanh bên).
 */
export function WorkspacesPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('workspaces.title'))
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const workspaces = useWorkspaces()
  const [search, setSearch] = useState('')
  const creating = params.has('new')

  const setCreating = (open: boolean) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (open) next.set('new', '1')
        else next.delete('new')
        return next
      },
      { replace: true },
    )

  const list = workspaces.data ?? []
  const visible = search.trim()
    ? list.filter((workspace) => matchesSearch(`${workspace.name} ${workspace.description}`, search))
    : list

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex max-w-xl flex-col gap-2">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('workspaces.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('workspaces.subtitle')}</p>
        </div>
        <Button
          variant="accent"
          onClick={() => setCreating(true)}
          icon={<Plus className="size-4" aria-hidden="true" />}
          data-testid="create-workspace"
        >
          {t('workspaces.create')}
        </Button>
      </header>

      {workspaces.isPending && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy="true">
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
          <Skeleton className="hidden h-36 xl:block" />
        </div>
      )}
      {workspaces.isError && (
        <Alert tone="danger">
          <p>{t('workspaces.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void workspaces.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {workspaces.isSuccess && list.length === 0 && (
        <EmptyState
          icon={<Layers />}
          title={t('workspaces.empty.title')}
          description={t('workspaces.empty.body')}
          action={
            <Button variant="dark" onClick={() => setCreating(true)} icon={<Plus className="size-4" aria-hidden="true" />}>
              {t('workspaces.empty.action')}
            </Button>
          }
          className="border-ink/50 py-14"
        />
      )}

      {list.length > 0 && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm font-medium tabular-nums" data-testid="workspaces-summary" aria-live="polite">
              {search.trim()
                ? t('workspaces.summaryFiltered', {
                    shown: visible.length.toLocaleString(i18n.language),
                    total: list.length.toLocaleString(i18n.language),
                  })
                : t('workspaces.summary', { count: list.length, formatted: list.length.toLocaleString(i18n.language) })}
            </p>
            {list.length >= SEARCH_FROM && (
              <TextField
                type="search"
                className="w-full sm:w-72"
                label={t('workspaces.search')}
                value={search}
                autoComplete="off"
                onChange={(event) => setSearch(event.target.value)}
                data-testid="workspaces-search"
              />
            )}
          </div>
          {visible.length === 0 ? (
            <EmptyState
              icon={<SearchX />}
              title={t('workspaces.noMatch.title')}
              description={t('workspaces.noMatch.body')}
              action={
                <Button variant="outline" onClick={() => setSearch('')}>
                  {t('workspaces.noMatch.clear')}
                </Button>
              }
            />
          ) : (
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" data-testid="workspace-grid">
              {visible.map((workspace) => (
                <li key={workspace.id}>
                  <WorkspaceCard workspace={workspace} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      <CreateWorkspaceDialog
        open={creating}
        onClose={() => setCreating(false)}
        onSaved={(workspace) => void navigate(workspacePath(workspace.id))}
      />
    </div>
  )
}
