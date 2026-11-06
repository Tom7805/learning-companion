import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { DocumentChunk } from '../../types'
import { HighlightedText } from './FindInDocument'

export function ReaderView({ chunks, page, target, term, zoom }: {
  chunks: DocumentChunk[]; page: number; target: DocumentChunk | null; term: string; zoom: number
}) {
  const { t, i18n } = useTranslation()
  const active = useRef<HTMLElement>(null)
  useEffect(() => { active.current?.scrollIntoView({ block: 'nearest' }) }, [target, page])
  return <div className="flex flex-col gap-3" data-testid="reader-view" style={{ fontSize: `${zoom}rem` }}>
    {chunks.filter((chunk) => chunk.location.number <= page && chunk.location.end >= page).map((chunk, index) =>
      <article key={chunk.ordinal} ref={chunk.ordinal === target?.ordinal ? active : undefined}
        data-testid="viewer-passage" data-highlighted={chunk.ordinal === target?.ordinal ? 'true' : undefined}
        className={`rounded-field border p-4 leading-relaxed whitespace-pre-wrap [overflow-wrap:anywhere] ${chunk.ordinal === target?.ordinal ? 'border-ink bg-sun-soft ring-2 ring-sun' : 'border-line bg-surface'}`}>
        <p className="mb-2 text-xs font-semibold text-muted">{t('library.viewer.passage', { number: (index + 1).toLocaleString(i18n.language) })}</p>
        {chunk.heading && <h3 className="mb-2 font-semibold">{chunk.heading}</h3>}
        <HighlightedText text={chunk.content} term={term} />
      </article>)}
  </div>
}
