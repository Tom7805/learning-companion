import { ChevronLeft, ChevronRight, Minus, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function ViewerToolbar({ page, pages, zoom, onTurn, onPage, onZoom }: {
  page: number; pages: number; zoom: number; onTurn: (delta: number) => void
  onPage: (page: number) => void; onZoom: (zoom: number) => void
}) {
  const { t, i18n } = useTranslation()
  return <div className="flex flex-wrap items-center justify-between gap-2 rounded-field border border-line bg-paper p-2" data-testid="viewer-toolbar">
    <div className="flex items-center gap-1">
      <button type="button" aria-label={t('library.viewer.previous')} disabled={page <= 1} onClick={() => onTurn(-1)}
        className="rounded-lg p-2 hover:bg-sun-soft disabled:opacity-40"><ChevronLeft className="size-5" /></button>
      <label className="flex items-center gap-1 text-sm">
        <span className="sr-only">{t('library.viewer.page')}</span>
        <input type="number" min={1} max={pages} value={page} onChange={(event) => onPage(Math.min(pages, Math.max(1, Number(event.target.value) || 1)))}
          className="w-14 rounded-md border border-ink bg-surface px-1 py-1 text-center tabular-nums" data-testid="viewer-page" />
        <span>/ {pages.toLocaleString(i18n.language)}</span>
      </label>
      <button type="button" aria-label={t('library.viewer.next')} disabled={page >= pages} onClick={() => onTurn(1)}
        className="rounded-lg p-2 hover:bg-sun-soft disabled:opacity-40" data-testid="viewer-next-page"><ChevronRight className="size-5" /></button>
    </div>
    <div className="flex items-center gap-1">
      <button type="button" aria-label={t('library.viewer.zoomOut')} disabled={zoom <= .5} onClick={() => onZoom(Math.max(.5, zoom - .25))}
        className="rounded-lg p-2 hover:bg-sun-soft disabled:opacity-40"><Minus className="size-4" /></button>
      <span className="w-12 text-center text-xs tabular-nums">{Math.round(zoom * 100)}%</span>
      <button type="button" aria-label={t('library.viewer.zoomIn')} disabled={zoom >= 2} onClick={() => onZoom(Math.min(2, zoom + .25))}
        className="rounded-lg p-2 hover:bg-sun-soft disabled:opacity-40"><Plus className="size-4" /></button>
    </div>
  </div>
}
