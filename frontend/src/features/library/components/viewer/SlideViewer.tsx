import type { DocumentChunk } from '../../types'
import { ReaderView } from './ReaderView'

export function SlideViewer(props: { chunks: DocumentChunk[]; page: number; target: DocumentChunk | null; term: string; zoom: number }) {
  return <div data-testid="slide-viewer" className="mx-auto max-w-4xl border-[1.5px] border-ink bg-surface p-4 shadow-pop aspect-video overflow-auto">
    <ReaderView {...props} />
  </div>
}
