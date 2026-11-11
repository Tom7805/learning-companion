import { Library, SearchX, UploadCloud } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useLibraryDocuments } from '@/features/library/api/queries'
import { DocumentStatusBadge } from '@/features/library/components/DocumentStatusBadge'
import { UploadDropzone } from '@/features/library/components/UploadDropzone'
import { UploadQueue } from '@/features/library/components/UploadQueue'
import { useChunkedUpload } from '@/features/library/hooks/useChunkedUpload'
import type { LibraryQuery } from '@/features/library/types'
import { cn } from '@/shared/lib/cn'
import { matchesSearch } from '@/shared/lib/vietnamese'
import { Button, Checkbox, Dialog, EmptyState, Skeleton, TextField } from '@/shared/ui'
import type { ScopeDocument } from '../types'

type Tab = 'library' | 'upload'
const TABS: Tab[] = ['library', 'upload']

const ALL_DOCUMENTS: LibraryQuery = {
  scope: 'ALL',
  folderId: null,
  types: [],
  tagIds: [],
  statuses: [],
  sort: 'OPENED',
  direction: 'DESC',
}

/**
 * Đính kèm tài liệu cho hội thoại mới (NCL-07-CN-001 TC-02): chọn trong kho (ô tìm không phân biệt dấu) hoặc tải tệp
 * mới. Tệp tải lên vào thư mục gốc của kho và được chọn luôn; trợ lý dùng được khi tệp xử lý xong.
 */
export function DocumentPickerDialog({
  open,
  selected,
  onClose,
  onApply,
  onAdd,
}: {
  open: boolean
  selected: ScopeDocument[]
  onClose: () => void
  /** Chọn xong trong kho: thay toàn bộ danh sách đính kèm. */
  onApply: (documents: ScopeDocument[]) => void
  /** Một tệp vừa tải lên xong (hay trùng với bản đã có): thêm vào danh sách, hộp thoại vẫn mở. */
  onAdd: (document: ScopeDocument) => void
}) {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('library')
  const baseId = useId()
  const refs = useRef<Record<Tab, HTMLButtonElement | null>>({ library: null, upload: null })

  const select = (next: Tab) => {
    setTab(next)
    refs.current[next]?.focus()
  }
  const onKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      select(tab === 'library' ? 'upload' : 'library')
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t('assistant.picker.title')}
      description={t('assistant.picker.description')}
      className="w-[min(96vw,720px)]"
    >
      <div className="flex flex-col gap-4" data-testid="document-picker">
        <div role="tablist" aria-label={t('assistant.picker.tabs')} className="grid grid-cols-2 gap-1 rounded-field border-[1.5px] border-ink bg-field p-1">
          {TABS.map((name) => {
            const on = tab === name
            const Icon = name === 'library' ? Library : UploadCloud
            return (
              <button
                key={name}
                ref={(element) => {
                  refs.current[name] = element
                }}
                type="button"
                role="tab"
                id={`${baseId}-tab-${name}`}
                aria-selected={on}
                aria-controls={`${baseId}-panel`}
                tabIndex={on ? 0 : -1}
                onClick={() => setTab(name)}
                onKeyDown={onKey}
                data-testid={`picker-tab-${name}`}
                className={cn(
                  'inline-flex h-10 items-center justify-center gap-2 rounded-[10px] text-[0.9375rem] font-medium',
                  on ? 'border-[1.5px] border-ink bg-surface text-ink shadow-[2px_2px_0_var(--color-ink)]' : 'text-ink-soft hover:bg-ink/5',
                )}
              >
                <Icon className="size-4" aria-hidden="true" />
                {t(`assistant.picker.tab.${name}`)}
              </button>
            )
          })}
        </div>
        <div role="tabpanel" id={`${baseId}-panel`} aria-labelledby={`${baseId}-tab-${tab}`}>
          {tab === 'library' ? (
            <LibraryList selected={selected} onCancel={onClose} onApply={onApply} />
          ) : (
            <UploadPanel onUploaded={onAdd} onClose={onClose} />
          )}
        </div>
      </div>
    </Dialog>
  )
}

function LibraryList({
  selected,
  onCancel,
  onApply,
}: {
  selected: ScopeDocument[]
  onCancel: () => void
  onApply: (documents: ScopeDocument[]) => void
}) {
  const { t, i18n } = useTranslation()
  const documents = useLibraryDocuments(ALL_DOCUMENTS)
  const [search, setSearch] = useState('')
  const [chosen, setChosen] = useState(() => new Set(selected.map((document) => document.id)))
  const searchField = useRef<HTMLInputElement>(null)
  const list = useMemo(() => documents.data ?? [], [documents.data])
  const visible = search.trim() ? list.filter((document) => matchesSearch(document.title, search)) : list

  useEffect(() => {
    const timer = window.setTimeout(() => searchField.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [])

  const apply = () => {
    const byId = new Map(list.map((document) => [document.id, document]))
    const kept = selected.filter((document) => chosen.has(document.id) && !byId.has(document.id))
    const picked = [...chosen].flatMap((id) => {
      const document = byId.get(id)
      return document ? [{ id, title: document.title, type: document.type, status: document.status ?? 'READY' }] : []
    })
    onApply([...kept, ...picked])
  }

  return (
    <div className="flex flex-col gap-3">
      <TextField
        ref={searchField}
        type="search"
        label={t('assistant.picker.search')}
        value={search}
        autoComplete="off"
        onChange={(event) => setSearch(event.target.value)}
        data-testid="picker-search"
      />
      <div className="flex max-h-[44dvh] min-h-32 flex-col overflow-y-auto rounded-field border-[1.5px] border-line">
        {documents.isPending && (
          <div className="flex flex-col gap-2 p-3">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        )}
        {documents.data && visible.length === 0 && (
          <EmptyState
            size="compact"
            className="m-auto"
            icon={<SearchX />}
            title={search.trim() ? t('assistant.picker.noMatch') : t('assistant.picker.empty')}
          />
        )}
        {visible.length > 0 && (
          <ul className="flex flex-col divide-y divide-line">
            {visible.map((document) => (
              <li key={document.id} className={cn('px-3 py-2', chosen.has(document.id) && 'bg-sun-soft')} data-testid="picker-option" data-title={document.title}>
                <Checkbox
                  checked={chosen.has(document.id)}
                  onChange={() =>
                    setChosen((current) => {
                      const next = new Set(current)
                      if (next.has(document.id)) next.delete(document.id)
                      else next.add(document.id)
                      return next
                    })
                  }
                  label={
                    <span className="flex flex-col gap-0.5">
                      <span className="font-medium text-ink [overflow-wrap:anywhere]">{document.title}</span>
                      <span className="flex flex-wrap items-center gap-2 text-[0.8125rem] text-muted">
                        {t(`library.type.${document.type}`)}
                        <DocumentStatusBadge status={document.status} />
                      </span>
                    </span>
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted" aria-live="polite">
          {t('assistant.picker.selected', { count: chosen.size, formatted: chosen.size.toLocaleString(i18n.language) })}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <Button variant="outline" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button variant="accent" onClick={apply} data-testid="picker-apply">
            {t('assistant.picker.apply')}
          </Button>
        </div>
      </div>
    </div>
  )
}

function UploadPanel({ onUploaded, onClose }: { onUploaded: (document: ScopeDocument) => void; onClose: () => void }) {
  const { t } = useTranslation()
  const uploads = useChunkedUpload()
  const [handled] = useState(() => new Set<string>())
  const root = t('library.shortcuts.root')

  useEffect(() => {
    for (const item of uploads.items) {
      const document = item.state === 'done' ? item.document : undefined
      const duplicate = item.state === 'duplicate' ? item.duplicate : undefined
      if (handled.has(item.key) || (!document && !duplicate)) continue
      handled.add(item.key)
      if (document) {
        onUploaded({ id: document.id, title: document.title, type: document.type, status: document.status ?? 'PENDING' })
      } else if (duplicate) {
        onUploaded({ id: duplicate.documentId, title: duplicate.title, type: 'PDF', status: 'READY' })
      }
    }
  }, [uploads.items, handled, onUploaded])

  return (
    <div className="flex flex-col gap-3">
      <UploadDropzone targetName={root} active={false} onFiles={(files) => uploads.add(files, null, root)} />
      <p className="text-[0.8125rem] text-muted">{t('assistant.picker.uploadHint')}</p>
      <UploadQueue items={uploads.items} onCancel={uploads.cancel} onRetry={uploads.retry} onClear={uploads.clearFinished} />
      <div className="flex justify-end">
        <Button variant="outline" onClick={onClose}>
          {t('assistant.picker.done')}
        </Button>
      </div>
    </div>
  )
}
