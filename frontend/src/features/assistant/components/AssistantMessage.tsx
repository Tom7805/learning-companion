import { AlertTriangle, Check, Copy, Download, FileSearch, FileText, Library, RotateCcw, Sparkles } from 'lucide-react'
import { Fragment, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SafetyNotice } from '@/features/safety'
import { cn } from '@/shared/lib/cn'
import { AiLabel, Button, Spinner, toast } from '@/shared/ui'
import type { ChatMessage, Citation } from '../types'
import { isAiGenerated } from '../lib/aiLabel'
import { citedDocuments, locationLabel } from '../lib/citations'
import { CitationMarker } from './CitationPopover'
import { SuggestedQuestions } from './SuggestedQuestions'

const MARKER = /\[(\d{1,2}(?:\s*[,;]\s*\d{1,2})*)]/g

/**
 * Một câu trả lời của trợ lý (NCL-07-CN-001): chữ hiện dần, số trích dẫn bấm được để mở đúng đoạn ở khung xem bên,
 * danh sách nguồn đã trích, ba câu hỏi gợi ý, và các thao tác sao chép, trả lời lại. Trường hợp không tìm thấy, chưa
 * chọn nguồn, lỗi hay bị bộ lọc an toàn can thiệp đều có lời giải thích riêng. Nội dung do mô hình sinh ra luôn có
 * nhãn "Do AI sinh", kể cả khi mở lại hội thoại cũ, và tải về được dạng Markdown có dấu nhận biết (NCL-04-CN-005).
 */
export function AssistantMessage({
  message,
  last,
  busy,
  onOpenCitation,
  onSuggestion,
  onRegenerate,
  onChooseSources,
  onAddDocument,
  onDownload,
}: {
  message: ChatMessage
  last: boolean
  busy: boolean
  onOpenCitation: (citation: Citation) => void
  onSuggestion: (question: string) => void
  onRegenerate: () => void
  onChooseSources?: () => void
  onAddDocument?: (document: { id: string; title: string }) => void
  /** Tải câu trả lời về; không có thì không hiện nút (câu trả lời chưa được máy chủ lưu). */
  onDownload?: (message: ChatMessage) => Promise<void>
}) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const generated = isAiGenerated(message)
  const streaming = message.status === 'STREAMING'
  const cited = new Map(message.citations.map((citation) => [citation.number, citation]))

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(message.content)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t('assistant.answer.copyFailed'))
    }
  }

  const download = async () => {
    if (!onDownload) return
    setDownloading(true)
    try {
      await onDownload(message)
    } finally {
      setDownloading(false)
    }
  }

  let body: ReactNode
  if (message.status === 'NO_SOURCES') {
    body = (
      <Notice icon={<Library className="size-5" aria-hidden="true" />} testId="answer-no-sources">
        <p>{t('assistant.answer.noSources')}</p>
        {onChooseSources && (
          <Button variant="outline" className="mt-3" onClick={onChooseSources}>
            {t('assistant.sources.choose')}
          </Button>
        )}
      </Notice>
    )
  } else if (message.status === 'NOT_FOUND') {
    body = (
      <Notice icon={<FileSearch className="size-5" aria-hidden="true" />} testId="answer-not-found">
        <p>{t('assistant.answer.notFound')}</p>
        {message.elsewhere && message.elsewhere.length > 0 && (
          <div className="mt-3 flex flex-col gap-2" data-testid="answer-elsewhere">
            <p className="text-sm text-ink-soft">{t('assistant.answer.elsewhere')}</p>
            <ul className="flex flex-wrap gap-2">
              {message.elsewhere.map((document) => (
                <li key={document.id}>
                  <Button
                    variant="outline"
                    className="h-auto min-h-11 py-2 text-left"
                    onClick={() => onAddDocument?.(document)}
                    disabled={!onAddDocument}
                    icon={<FileText className="size-4 shrink-0" aria-hidden="true" />}
                  >
                    {t('assistant.answer.addDocument', { title: document.title })}
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Notice>
    )
  } else if ((message.status === 'BLOCKED' || message.status === 'SUPPORT') && message.safety) {
    body = <SafetyNotice outcome={message.safety} />
  } else if (message.status === 'BLOCKED' || message.status === 'SUPPORT') {
    body = <Notice icon={<AlertTriangle className="size-5" aria-hidden="true" />}>{t('assistant.answer.blocked')}</Notice>
  } else {
    body = (
      <div className="whitespace-pre-wrap text-[0.9375rem] leading-7 text-ink [overflow-wrap:anywhere]" data-testid="answer-text">
        {renderWithCitations(message.content, cited, onOpenCitation)}
        {streaming && (
          <span className="ml-1 inline-flex translate-y-0.5 items-center" aria-hidden="true">
            <Spinner className="size-4" />
          </span>
        )}
      </div>
    )
  }

  return (
    <article
      className="flex flex-col gap-3"
      aria-busy={streaming || undefined}
      data-testid="assistant-message"
      data-status={message.status}
    >
      <div className="flex items-center gap-2 text-sm font-medium text-ink-soft">
        <span aria-hidden="true" className="grid size-7 place-items-center rounded-lg border-[1.5px] border-ink bg-sun">
          <Sparkles className="size-4" />
        </span>
        {t('assistant.answer.author')}
        {generated && <AiLabel />}
        {streaming && message.content === '' && <span className="text-muted">{t('assistant.answer.thinking')}</span>}
      </div>
      {body}

      {message.status === 'STOPPED' && <p className="text-sm text-muted" data-testid="answer-stopped">{t('assistant.answer.stopped')}</p>}

      {message.status === 'FAILED' && (
        <div role="alert" className="flex flex-col gap-2 rounded-field border border-danger/40 bg-danger-soft px-4 py-3 text-sm" data-testid="answer-failed">
          <p>{t(`assistant.errors.${message.errorCode ?? 'INTERNAL_ERROR'}`, {
            defaultValue: t(`errors.${message.errorCode ?? 'INTERNAL_ERROR'}`, { defaultValue: t('assistant.errors.INTERNAL_ERROR') }),
          })}</p>
          {message.errorCode === 'AI_TEMPORARILY_UNAVAILABLE' && <p className="text-ink-soft">{t('assistant.answer.refunded')}</p>}
          {last && (
            <Button
              variant="outline"
              className="self-start"
              onClick={onRegenerate}
              disabled={busy}
              icon={<RotateCcw className="size-4" aria-hidden="true" />}
              data-testid="answer-retry"
            >
              {t('common.retry')}
            </Button>
          )}
        </div>
      )}

      {message.citations.length > 0 && !streaming && (
        <section aria-label={t('assistant.answer.sources')} className="flex flex-col gap-1.5" data-testid="answer-sources">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted">{t('assistant.answer.sources')}</h3>
          <ol className="flex flex-col gap-1">
            {citedDocuments(message.citations).map(({ key, first, numbers }) => (
              <li key={key} data-testid="answer-source" data-available={first.available}>
                <button
                  type="button"
                  onClick={() => onOpenCitation(first)}
                  disabled={!first.available}
                  className="flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-ink/5 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                >
                  <span className="flex shrink-0 gap-1">
                    {numbers.map((number) => (
                      <span key={number} className="grid min-w-6 place-items-center rounded-md border-[1.5px] border-ink bg-sun px-1 text-xs font-semibold tabular-nums">
                        {number}
                      </span>
                    ))}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className={cn('font-medium [overflow-wrap:anywhere]', !first.available && 'text-muted line-through')}>
                      {first.documentTitle}
                    </span>
                    {first.available ? (
                      <span className="text-muted"> · {locationLabel(first, t)}</span>
                    ) : (
                      <span className="text-danger"> · {t('assistant.citation.gone')}</span>
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </section>
      )}

      {message.suggestions.length > 0 && !streaming && last && (
        <SuggestedQuestions questions={message.suggestions} disabled={busy} onPick={onSuggestion} />
      )}

      {!streaming && message.content && (message.status === 'COMPLETE' || message.status === 'STOPPED') && (
        <div className="flex flex-wrap gap-1">
          <Button
            variant="ghost"
            className="h-9 px-3 text-sm"
            onClick={() => void copy()}
            icon={copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
            data-testid="answer-copy"
          >
            {copied ? t('assistant.answer.copied') : t('assistant.answer.copy')}
          </Button>
          {generated && onDownload && (
            <Button
              variant="ghost"
              className="h-9 px-3 text-sm"
              onClick={() => void download()}
              disabled={downloading}
              aria-label={t('assistant.answer.downloadLabel')}
              icon={downloading ? <Spinner className="size-4" /> : <Download className="size-4" aria-hidden="true" />}
              data-testid="answer-download"
            >
              {downloading ? t('assistant.answer.downloading') : t('assistant.answer.download')}
            </Button>
          )}
          {last && (
            <Button
              variant="ghost"
              className="h-9 px-3 text-sm"
              onClick={onRegenerate}
              disabled={busy}
              icon={<RotateCcw className="size-4" aria-hidden="true" />}
              data-testid="answer-regenerate"
            >
              {t('assistant.answer.regenerate')}
            </Button>
          )}
        </div>
      )}
    </article>
  )
}

function Notice({ icon, children, testId }: { icon: ReactNode; children: ReactNode; testId?: string }) {
  return (
    <div className="flex items-start gap-3 rounded-field border-[1.5px] border-ink bg-sky-soft px-4 py-3 text-[0.9375rem] leading-relaxed" data-testid={testId}>
      {icon}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
}

/** Thay các số trích dẫn [1], [2, 3] bằng nút bấm mở đúng đoạn; số không có nguồn thì để nguyên chữ. */
function renderWithCitations(
  text: string,
  cited: Map<number, Citation>,
  onOpen: (citation: Citation) => void,
) {
  const parts: ReactNode[] = []
  let cursor = 0
  for (const match of text.matchAll(MARKER)) {
    const numbers = match[1].split(/[,;]/).map((part) => Number(part.trim()))
    if (!numbers.every((number) => cited.has(number))) continue
    parts.push(text.slice(cursor, match.index))
    parts.push(
      <Fragment key={match.index}>
        {numbers.map((number) => (
          <CitationMarker key={number} citation={cited.get(number)!} onOpen={onOpen} />
        ))}
      </Fragment>,
    )
    cursor = match.index + match[0].length
  }
  parts.push(text.slice(cursor))
  return parts
}
