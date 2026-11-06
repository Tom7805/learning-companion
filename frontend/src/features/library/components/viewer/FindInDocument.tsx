import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function FindInDocument({ term, onTerm, count, active, onNext }: {
  term: string; onTerm: (value: string) => void; count: number; active: number; onNext: () => void
}) {
  const { t, i18n } = useTranslation()
  return <div className="flex flex-wrap items-center gap-2" role="search" aria-label={t('library.viewer.find')}>
    <label className="flex min-w-0 flex-1 items-center gap-2 rounded-field border border-ink bg-surface px-3">
      <Search className="size-4 shrink-0" aria-hidden="true" />
      <span className="sr-only">{t('library.viewer.find')}</span>
      <input value={term} onChange={(event) => onTerm(event.target.value)} placeholder={t('library.viewer.find')}
        className="h-10 min-w-0 flex-1 bg-transparent text-sm outline-none" data-testid="viewer-search" />
    </label>
    <span aria-live="polite" className="min-w-12 text-center text-xs tabular-nums">
      {term ? `${count ? (active + 1).toLocaleString(i18n.language) : '0'} / ${count.toLocaleString(i18n.language)}` : ''}
    </span>
    <button type="button" onClick={onNext} disabled={!count} className="rounded-field border border-ink px-3 py-2 text-sm disabled:opacity-40"
      data-testid="viewer-next-result">{t('library.viewer.nextResult')}</button>
  </div>
}

/** Bôi sáng mọi lần xuất hiện, không diễn giải HTML của tài liệu. */
export function HighlightedText({ text, term }: { text: string; term: string }) {
  const needle = term.trim()
  if (!needle) return text
  const parts: React.ReactNode[] = []
  const lower = text.toLocaleLowerCase()
  let from = 0
  let at = lower.indexOf(needle.toLocaleLowerCase())
  while (at !== -1) {
    parts.push(text.slice(from, at))
    parts.push(<mark key={at} className="rounded bg-sun px-0.5">{text.slice(at, at + needle.length)}</mark>)
    from = at + needle.length
    at = lower.indexOf(needle.toLocaleLowerCase(), from)
  }
  parts.push(text.slice(from))
  return <>{parts}</>
}
