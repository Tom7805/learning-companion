import { Check, FileText, Paperclip, X } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import type { ScopeDocument } from '../types'

/**
 * Các tài liệu trợ lý được dùng để trả lời (QTN-22): mỗi tài liệu một thẻ bật tắt được. Tài liệu chưa xử lý xong không
 * chọn được và nói rõ trạng thái. Ở hội thoại riêng có nút đính kèm thêm tài liệu và nút bỏ tài liệu khỏi danh sách.
 */
export function SourceScopePicker({
  documents,
  selected,
  onToggle,
  onAttach,
  onRemove,
}: {
  documents: ScopeDocument[]
  selected: Set<string>
  onToggle: (id: string) => void
  onAttach?: () => void
  onRemove?: (id: string) => void
}) {
  const { t } = useTranslation()
  const id = useId()
  const ready = documents.filter((document) => document.status === 'READY')
  const chosen = ready.filter((document) => selected.has(document.id)).length

  return (
    <section aria-labelledby={id} className="flex flex-col gap-2" data-testid="source-scope">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={id} className="text-sm font-medium" data-testid="source-scope-summary">
          {documents.length
            ? t('assistant.sources.summary', { count: chosen, total: ready.length })
            : t('assistant.sources.none')}
        </h2>
        {onAttach && (
          <button
            type="button"
            onClick={onAttach}
            data-testid="attach-sources"
            className="inline-flex min-h-11 items-center gap-2 rounded-xl px-3 text-sm font-medium text-ink-soft hover:bg-ink/5 hover:text-ink"
          >
            <Paperclip className="size-4" aria-hidden="true" />
            {t('assistant.sources.attach')}
          </button>
        )}
      </div>
      {documents.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {documents.map((document) => {
            const usable = document.status === 'READY'
            const on = usable && selected.has(document.id)
            return (
              <li key={document.id} className="flex max-w-full items-stretch" data-testid="source-chip" data-title={document.title}>
                <button
                  type="button"
                  role="switch"
                  aria-checked={on}
                  disabled={!usable}
                  onClick={() => onToggle(document.id)}
                  title={usable ? undefined : t(`library.status.${document.status}`)}
                  className={cn(
                    'inline-flex min-h-11 max-w-full items-center gap-2 border-[1.5px] px-3 text-sm',
                    onRemove ? 'rounded-l-xl border-r-0' : 'rounded-xl',
                    on ? 'border-ink bg-sun font-medium text-ink' : 'border-line bg-surface text-ink-soft',
                    usable ? 'hover:border-ink' : 'cursor-not-allowed opacity-60',
                  )}
                >
                  {on ? <Check className="size-4 shrink-0" aria-hidden="true" /> : <FileText className="size-4 shrink-0" aria-hidden="true" />}
                  <span className="truncate">{document.title}</span>
                  {!usable && <span className="shrink-0 text-xs">({t(`library.status.${document.status}`)})</span>}
                </button>
                {onRemove && (
                  <button
                    type="button"
                    onClick={() => onRemove(document.id)}
                    aria-label={t('assistant.sources.remove', { title: document.title })}
                    className={cn(
                      'grid min-h-11 w-9 place-items-center rounded-r-xl border-[1.5px] text-muted hover:text-ink',
                      on ? 'border-ink bg-sun' : 'border-line bg-surface',
                    )}
                  >
                    <X className="size-4" aria-hidden="true" />
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
