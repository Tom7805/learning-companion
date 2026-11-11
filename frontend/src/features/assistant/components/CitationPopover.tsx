import { ExternalLink, FileX } from 'lucide-react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { cn } from '@/shared/lib/cn'
import { Button, Drawer } from '@/shared/ui'
import { useCitation } from '../api/queries'
import { locationLabel } from '../lib/citations'
import type { Citation } from '../types'

/** Chờ chừng này sau khi rê chuột vào hay ra khỏi số trích dẫn, để lướt qua không làm khung bật tắt liên tục. */
const HOVER_DELAY_MS = 150

/**
 * Một số trích dẫn trong câu trả lời (NCL-07-CN-002). Trên máy tính, rê chuột hay chuyển tiêu điểm tới là hiện khung
 * xem nhanh (tên tài liệu, vị trí, đoạn gốc với phần liên quan được tô sáng); bấm là mở tài liệu ở khung xem bên đúng
 * trang và đoạn. Trên điện thoại, chạm là mở ngăn trượt từ dưới lên với nút mở tài liệu. Tài liệu đã bị xóa thì báo
 * "tài liệu không còn" thay vì mở khung xem lỗi (TC-03).
 */
export function CitationMarker({ citation, onOpen }: { citation: Citation; onOpen: (citation: Citation) => void }) {
  const { t } = useTranslation()
  const touch = useMediaQuery('(pointer: coarse)')
  const narrow = !useMediaQuery('(min-width: 768px)')
  const sheet = touch || narrow
  const [open, setOpen] = useState(false)
  const [sheetOpen, setSheetOpen] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  const popoverId = useId()
  const preview = useCitation(citation, open || sheetOpen)
  const current = preview.data ?? citation
  const title = current.available ? current.documentTitle : t('assistant.citation.gone')

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const later = (next: boolean) => {
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setOpen(next), HOVER_DELAY_MS)
  }

  const activate = () => {
    if (sheet) {
      setSheetOpen(true)
      return
    }
    if (current.available) {
      setOpen(false)
      onOpen(current)
    } else {
      setOpen(true)
    }
  }

  return (
    <span
      className="relative inline-block"
      onMouseEnter={sheet ? undefined : () => later(true)}
      onMouseLeave={sheet ? undefined : () => later(false)}
    >
      <button
        type="button"
        onClick={activate}
        onFocus={sheet ? undefined : () => later(true)}
        onBlur={sheet ? undefined : () => later(false)}
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false)
        }}
        data-testid="citation-marker"
        data-number={citation.number}
        data-available={current.available}
        aria-describedby={open ? popoverId : undefined}
        aria-label={t('assistant.answer.citation', { number: citation.number, title, location: locationLabel(current, t) })}
        className={cn(
          'mx-0.5 inline-grid min-h-6 min-w-6 -translate-y-0.5 place-items-center rounded-md border-[1.5px] border-ink px-1',
          'align-baseline text-xs font-semibold tabular-nums',
          current.available ? 'bg-sun hover:bg-ink hover:text-on-ink' : 'bg-surface text-muted line-through',
        )}
      >
        {citation.number}
      </button>
      {open && !sheet && (
        <span
          id={popoverId}
          role="tooltip"
          data-testid="citation-popover"
          className="absolute left-1/2 top-full z-30 mt-2 block w-[min(26rem,80vw)] -translate-x-1/2 rounded-card border-[1.5px] border-ink bg-surface p-4 text-left text-sm font-normal leading-relaxed text-ink shadow-pop"
          onMouseEnter={() => later(true)}
          onMouseLeave={() => later(false)}
        >
          <CitationPreview citation={current} />
        </span>
      )}
      {sheet && (
        <Drawer open={sheetOpen} onClose={() => setSheetOpen(false)} label={t('assistant.citation.sheet', { number: citation.number })} side="bottom">
          <div className="flex flex-col gap-4 bg-surface p-5 pb-8" data-testid="citation-sheet">
            <span aria-hidden="true" className="mx-auto h-1.5 w-12 rounded-full bg-line" />
            <CitationPreview citation={current} />
            <div className="flex flex-col gap-2">
              {current.available && (
                <Button
                  variant="accent"
                  onClick={() => {
                    setSheetOpen(false)
                    onOpen(current)
                  }}
                  icon={<ExternalLink className="size-4" aria-hidden="true" />}
                  data-testid="citation-open"
                >
                  {t('assistant.citation.open')}
                </Button>
              )}
              <Button variant="outline" onClick={() => setSheetOpen(false)}>
                {t('common.close')}
              </Button>
            </div>
          </div>
        </Drawer>
      )}
    </span>
  )
}

/** Nội dung khung xem nhanh: tên tài liệu, vị trí, đoạn gốc với phần liên quan được tô sáng. */
function CitationPreview({ citation }: { citation: Citation }) {
  const { t } = useTranslation()
  return (
    <span className="flex flex-col gap-2" data-testid="citation-preview">
      <span className="flex items-start gap-2">
        <span className="grid min-w-6 place-items-center rounded-md border-[1.5px] border-ink bg-sun px-1 text-xs font-semibold tabular-nums">
          {citation.number}
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="font-semibold [overflow-wrap:anywhere]" data-testid="citation-title">
            {citation.documentTitle}
          </span>
          <span className="text-[0.8125rem] text-muted" data-testid="citation-location">
            {locationLabel(citation, t)}
          </span>
        </span>
      </span>
      {!citation.available && (
        <span className="flex items-center gap-2 rounded-lg bg-danger-soft px-3 py-2 text-[0.8125rem]" data-testid="citation-gone">
          <FileX className="size-4 shrink-0 text-danger" aria-hidden="true" />
          {t('assistant.citation.goneBody')}
        </span>
      )}
      <span className="block max-h-56 overflow-y-auto whitespace-pre-wrap rounded-lg border border-line bg-paper px-3 py-2 text-[0.8125rem] leading-relaxed text-ink-soft" data-testid="citation-excerpt">
        {highlighted(citation)}
      </span>
    </span>
  )
}

function highlighted(citation: Citation): ReactNode {
  const range = citation.highlight
  const text = citation.excerpt
  if (!range || range.start < 0 || range.end > text.length || range.start >= range.end) return text
  return (
    <>
      {text.slice(0, range.start)}
      <mark className="rounded bg-sun px-0.5 text-ink" data-testid="citation-highlight">
        {text.slice(range.start, range.end)}
      </mark>
      {text.slice(range.end)}
    </>
  )
}
