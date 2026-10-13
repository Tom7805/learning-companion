import { FileText, Search } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { navigation } from '@/app/navigation'
import { documentPath } from '@/app/routes'
import { useDocuments } from '@/features/library'
import { cn } from '@/shared/lib/cn'
import { matchesSearch } from '@/shared/lib/vietnamese'
import { Spinner } from '@/shared/ui'

interface Result {
  key: string
  group: 'pages' | 'documents'
  label: string
  to: string
  icon: typeof FileText
}

const DOCUMENT_LIMIT = 8

/**
 * "Mở nhanh" — nút hành động chính của thanh bên (NCL-02-CN-001): gõ vài chữ để tới ngay một trang hoặc một tài
 * liệu, không phân biệt dấu. Hộp thoại theo mẫu combobox: phím mũi tên chọn, Enter mở, Esc đóng.
 */
export function QuickOpen({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const element = dialog.current
    if (!element) return
    if (open && !element.open) {
      if (typeof element.showModal === 'function') element.showModal()
      else element.setAttribute('open', '')
      // Ô tìm được dựng khi hộp thoại còn đóng nên autoFocus không có tác dụng; đưa tiêu điểm vào sau khi mở.
      element.querySelector<HTMLInputElement>('input[role="combobox"]')?.focus()
    } else if (!open && element.open) {
      if (typeof element.close === 'function') element.close()
      else element.removeAttribute('open')
    }
  }, [open])

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      data-testid="quick-open"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      className="mx-auto mt-[12vh] w-[min(92vw,560px)] rounded-card border-[1.5px] border-ink bg-surface p-0 text-ink shadow-pop backdrop:bg-scrim/45"
    >
      <h2 id={titleId} className="sr-only">
        {t('shell.quickOpen')}
      </h2>
      {/* Chỉ dựng (và tải danh sách tài liệu) khi đang mở; mở lại là bắt đầu với ô tìm trống. */}
      {open && <QuickOpenSearch onClose={onClose} />}
    </dialog>
  )
}

function QuickOpenSearch({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listId = useId()
  const documents = useDocuments()

  const results = useMemo<Result[]>(() => {
    const pages = navigation
      .map((entry) => ({ key: `page:${entry.id}`, group: 'pages' as const, label: t(entry.label), to: entry.to, icon: entry.icon }))
      .filter((entry) => matchesSearch(entry.label, query))
    const docs = (documents.data ?? [])
      .filter((document) => matchesSearch(document.title, query))
      .slice(0, DOCUMENT_LIMIT)
      .map((document) => ({
        key: `document:${document.id}`,
        group: 'documents' as const,
        label: document.title,
        to: documentPath(document.id),
        icon: FileText,
      }))
    return [...pages, ...docs]
  }, [documents.data, query, t])

  const activeIndex = Math.min(active, Math.max(results.length - 1, 0))
  const go = (result: Result | undefined) => {
    if (!result) return
    onClose()
    navigate(result.to)
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((activeIndex + 1) % Math.max(results.length, 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((activeIndex - 1 + results.length) % Math.max(results.length, 1))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      go(results[activeIndex])
    }
  }

  const optionId = (index: number) => `${listId}-${index}`

  return (
    <div className="flex max-h-[70vh] flex-col">
      <div className="flex items-center gap-3 border-b border-line px-4">
        <Search className="size-5 shrink-0 text-muted" aria-hidden="true" />
        <input
          role="combobox"
          aria-expanded="true"
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={results.length ? optionId(activeIndex) : undefined}
          aria-label={t('quickOpen.label')}
          placeholder={t('quickOpen.placeholder')}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActive(0)
          }}
          onKeyDown={onKeyDown}
          className="h-14 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-subtle"
        />
        {documents.isFetching && <Spinner className="size-4 text-muted" />}
      </div>
      <ul id={listId} role="listbox" aria-label={t('quickOpen.results')} className="min-h-0 flex-1 overflow-y-auto p-2">
        {results.map((result, index) => (
          <li key={result.key} role="presentation">
            {(index === 0 || results[index - 1].group !== result.group) && (
              <p role="presentation" className="px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-muted">
                {t(`quickOpen.groups.${result.group}`)}
              </p>
            )}
            <div
              id={optionId(index)}
              role="option"
              aria-selected={index === activeIndex}
              onMouseMove={() => setActive(index)}
              onClick={() => go(result)}
              className={cn(
                'flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 text-sm',
                index === activeIndex ? 'bg-sun text-ink' : 'text-ink-soft',
              )}
            >
              <result.icon className="size-4 shrink-0" aria-hidden="true" />
              <span className="truncate">{result.label}</span>
            </div>
          </li>
        ))}
      </ul>
      {results.length === 0 && (
        <p role="status" className="px-6 pb-6 text-center text-sm text-muted">
          {t('quickOpen.empty')}
        </p>
      )}
      <p className="border-t border-line px-4 py-2.5 text-xs text-muted">{t('quickOpen.hint')}</p>
    </div>
  )
}
