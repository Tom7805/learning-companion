import type { DocumentChunk } from '../../types'
import { ReaderView } from './ReaderView'

export function ImageViewer({ url, ...props }: { url: string; chunks: DocumentChunk[]; page: number; target: DocumentChunk | null; term: string; zoom: number }) {
  return <div className="flex flex-col gap-4" data-testid="image-viewer">
    <img src={url} alt="" className="sticky top-28 z-[5] max-h-[42vh] w-full bg-surface object-contain" style={{ transform: `scale(${props.zoom})`, transformOrigin: 'top center' }} />
    <ReaderView {...props} />
  </div>
}
