import { useEffect, useMemo, useRef, useState, type TouchEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { documentPath } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { Alert, ErrorState, Skeleton } from '@/shared/ui'
import { ProcessingPanel } from '../DocumentProcessing'
import { useOriginalFile } from '../../hooks/useOriginalFile'
import { useDocumentViewer, type ViewerLocation } from '../../hooks/useDocumentViewer'
import { FindInDocument } from './FindInDocument'
import { findMatches } from './viewerSearch'
import { ImageViewer } from './ImageViewer'
import { PdfViewer } from './PdfViewer'
import { ReaderView } from './ReaderView'
import { SlideViewer } from './SlideViewer'
import { ViewerToolbar } from './ViewerToolbar'

const searchState = new Map<string, { term: string; index: number }>()

export function DocumentViewer({ id, location }: { id: string; location: ViewerLocation | null }) {
  const { t, i18n } = useTranslation()
  const panel = useSidePanel()
  const viewer = useDocumentViewer(id, location)
  const { open } = useOriginalFile(id)
  const [fileUrl, setFileUrl] = useState<string | null>(null)
  const [fileError, setFileError] = useState(false)
  const [term, setTerm] = useState(searchState.get(id)?.term ?? '')
  const [matchIndex, setMatchIndex] = useState(searchState.get(id)?.index ?? 0)
  const swipeX = useRef<number | null>(null)
  const type = viewer.query.data?.type
  const ready = viewer.query.data?.status === 'READY'

  useEffect(() => {
    if (!ready || (type !== 'PDF' && type !== 'IMAGE')) return undefined
    let cancelled = false
    let url: string | null = null
    void open().then((blob) => {
      if (cancelled) return
      url = URL.createObjectURL(blob)
      setFileUrl(url)
    }).catch(() => { if (!cancelled) setFileError(true) })
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url) }
  }, [ready, type, id, open])

  const matches = useMemo(() => findMatches(viewer.chunks, term), [viewer.chunks, term])
  const selected = matches.length ? matches[Math.min(matchIndex, matches.length - 1)].chunk : viewer.target
  const onTerm = (value: string) => {
    searchState.set(id, { term: value, index: 0 })
    setTerm(value)
    setMatchIndex(0)
    const first = findMatches(viewer.chunks, value)[0]
    if (first) viewer.setPage(first.chunk.location.number)
  }
  const onNext = () => {
    if (!matches.length) return
    const next = (matchIndex + 1) % matches.length
    searchState.set(id, { term, index: next })
    setMatchIndex(next)
    viewer.setPage(matches[next].chunk.location.number)
  }
  const onTouchStart = (event: TouchEvent<HTMLElement>) => { swipeX.current = event.touches[0]?.clientX ?? null }
  const onTouchEnd = (event: TouchEvent<HTMLElement>) => {
    if (swipeX.current === null) return
    const distance = (event.changedTouches[0]?.clientX ?? swipeX.current) - swipeX.current
    if (Math.abs(distance) > 70) viewer.turn(distance < 0 ? 1 : -1)
    swipeX.current = null
  }

  if (viewer.query.isPending) return <div aria-busy="true"><Skeleton className="h-40" /></div>
  if (viewer.query.isError) {
    const missing = toApiError(viewer.query.error).status === 404
    return <ErrorState title={t(missing ? 'library.notFound.title' : 'library.loadFailed')}
      description={missing ? t('library.notFound.body') : undefined}
      onRetry={missing ? undefined : () => void viewer.query.refetch()} />
  }
  const data = viewer.query.data
  if (data.status !== 'READY') {
    return <div data-testid="viewer-processing" className="space-y-4" aria-live="polite">
      <ProcessingPanel document={data} />
      {(data.status === 'PENDING' || data.status === 'PROCESSING') && <div>
        <p className="mb-2 text-sm">{t('library.viewer.progress', { percent: data.percent.toLocaleString(i18n.language) })}</p>
        <progress value={data.percent} max={100} className="w-full accent-sun" />
      </div>}
    </div>
  }

  const props = { chunks: viewer.chunks, page: viewer.page, target: selected, term, zoom: viewer.zoom }
  return <section className="flex flex-col gap-4" data-testid="document-peek" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
    <div data-testid="document-viewer" className="flex flex-col gap-4">
    <ProcessingPanel document={data} />
    <div className="sticky top-0 z-10 flex flex-col gap-2 bg-surface pb-2">
      <FindInDocument term={term} onTerm={onTerm} count={matches.length} active={matchIndex} onNext={onNext} />
      <ViewerToolbar page={viewer.page} pages={viewer.maxPage} zoom={viewer.zoom} onPage={viewer.setPage}
        onTurn={viewer.turn} onZoom={viewer.setZoom} />
    </div>
    {fileError && <Alert tone="danger">{t('library.file.openFailed')}</Alert>}
    {data.type === 'PDF' && (fileUrl ? <PdfViewer key={`${viewer.page}:${viewer.zoom}`} url={fileUrl} {...props} /> : <Skeleton className="h-80" />)}
    {data.type === 'IMAGE' && (fileUrl ? <ImageViewer url={fileUrl} {...props} /> : <Skeleton className="h-80" />)}
    {data.type === 'PPTX' && <SlideViewer {...props} />}
    {data.type !== 'PDF' && data.type !== 'IMAGE' && data.type !== 'PPTX' && <ReaderView {...props} />}
    </div>
    <Link to={documentPath(id)} onClick={() => panel.close(false)} className="self-start text-sm font-medium underline underline-offset-4">
      {t('library.peek.openFull')}
    </Link>
  </section>
}
