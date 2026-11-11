import { FileText, LayoutDashboard, ListTree, MessagesSquare, type LucideIcon } from 'lucide-react'
import { useRef, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { WORKSPACE_TABS, workspacePanelId, workspaceTabId, type WorkspaceTab } from '../lib/tabs'

const icons: Record<WorkspaceTab, LucideIcon> = {
  overview: LayoutDashboard,
  outline: ListTree,
  assistant: MessagesSquare,
  sources: FileText,
}

/** Hàng thẻ của trang không gian học: phím mũi tên, Home, End chuyển thẻ; số tài liệu hiện ngay trên thẻ Tài liệu. */
export function WorkspaceTabs({
  baseId,
  tab,
  documentCount,
  onChange,
}: {
  baseId: string
  tab: WorkspaceTab
  documentCount: number
  onChange: (tab: WorkspaceTab) => void
}) {
  const { t, i18n } = useTranslation()
  const refs = useRef<Partial<Record<WorkspaceTab, HTMLButtonElement | null>>>({})

  const select = (next: WorkspaceTab) => {
    onChange(next)
    refs.current[next]?.focus()
  }

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = WORKSPACE_TABS.indexOf(tab)
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      const step = event.key === 'ArrowRight' ? 1 : WORKSPACE_TABS.length - 1
      select(WORKSPACE_TABS[(index + step) % WORKSPACE_TABS.length])
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      select(event.key === 'Home' ? WORKSPACE_TABS[0] : WORKSPACE_TABS[WORKSPACE_TABS.length - 1])
    }
  }

  return (
    <div
      role="tablist"
      aria-label={t('workspaces.tabs.label')}
      className="flex max-w-full gap-1 self-start overflow-x-auto rounded-field border-[1.5px] border-ink bg-field p-1"
    >
      {WORKSPACE_TABS.map((name) => {
        const selected = tab === name
        const Icon = icons[name]
        return (
          <button
            key={name}
            ref={(element) => {
              refs.current[name] = element
            }}
            type="button"
            role="tab"
            id={workspaceTabId(baseId, name)}
            aria-selected={selected}
            aria-controls={workspacePanelId(baseId, name)}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(name)}
            onKeyDown={onKeyDown}
            data-testid={`workspace-tab-${name}`}
            className={cn(
              'inline-flex h-10 shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-[10px] px-3 text-[0.9375rem] font-medium sm:px-4',
              'transition-colors duration-150',
              selected
                ? 'border-[1.5px] border-ink bg-surface text-ink shadow-[2px_2px_0_var(--color-ink)]'
                : 'text-ink-soft hover:bg-ink/5',
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            {t(`workspaces.tabs.${name}`)}
            {name === 'sources' && documentCount > 0 && (
              <span className="tabular-nums text-muted" data-testid="workspace-tab-count">
                {documentCount.toLocaleString(i18n.language)}
              </span>
            )}
          </button>
        )
      })}
    </div>
  )
}
