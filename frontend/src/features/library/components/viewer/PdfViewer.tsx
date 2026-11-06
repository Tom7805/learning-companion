import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import type { DocumentChunk } from '../../types'
import { ReaderView } from './ReaderView'

/** Dựng đúng trang PDF từ tệp gốc trên canvas; chỉ giữ một trang trong bộ nhớ khi đọc giáo trình dài. */
export function PdfViewer({ url, ...props }: { url: string; chunks: DocumentChunk[]; page: number; target: DocumentChunk | null; term: string; zoom: number }) {
  const { t } = useTranslation()
  const host = useRef<HTMLDivElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const [rendered, setRendered] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    let cancelRender: (() => void) | undefined
    let destroyDocument: (() => Promise<void>) | undefined
    void (async () => {
      try {
        const pdfjs = await import('pdfjs-dist')
        pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
        const loading = pdfjs.getDocument({ url })
        destroyDocument = () => loading.destroy()
        const document = await loading.promise
        if (cancelled) return
        const page = await document.getPage(props.page)
        if (cancelled || !canvas.current || !host.current) return
        const unscaled = page.getViewport({ scale: 1 })
        const fit = Math.max(0.1, (host.current.clientWidth - 16) / unscaled.width)
        const displayScale = fit * props.zoom
        const dpr = Math.min(window.devicePixelRatio || 1, 2)
        const viewport = page.getViewport({ scale: displayScale * dpr })
        const element = canvas.current
        element.width = Math.ceil(viewport.width)
        element.height = Math.ceil(viewport.height)
        element.style.width = `${viewport.width / dpr}px`
        element.style.height = `${viewport.height / dpr}px`
        const context = element.getContext('2d')
        if (!context) throw new Error('Canvas unavailable')
        const task = page.render({ canvas: element, canvasContext: context, viewport })
        cancelRender = () => task.cancel()
        await task.promise
        if (!cancelled) setRendered(true)
      } catch (error) {
        if (!cancelled && !(error instanceof Error && error.name === 'RenderingCancelledException')) setFailed(true)
      }
    })()
    return () => { cancelled = true; cancelRender?.(); void destroyDocument?.() }
  }, [url, props.page, props.zoom])

  return <div className="flex flex-col gap-4" data-testid="pdf-viewer">
    <div ref={host} tabIndex={0} aria-label={t('library.viewer.pdfPage', { number: props.page })}
      className="sticky top-28 z-[5] max-h-[45vh] min-h-64 overflow-auto rounded-field border border-line bg-paper p-2 focus-visible:outline-4 focus-visible:outline-focus">
      {failed && <p role="alert" className="p-4 text-sm">{t('library.file.openFailed')}</p>}
      {!rendered && !failed && <p role="status" className="p-4 text-sm">{t('library.file.loading')}</p>}
      <canvas ref={canvas} data-testid="pdf-page" data-rendered={rendered ? 'true' : undefined}
        aria-label={t('library.viewer.pdfPage', { number: props.page })} role="img"
        className="mx-auto block max-w-none bg-surface shadow-sm" />
    </div>
    <ReaderView {...props} />
  </div>
}
