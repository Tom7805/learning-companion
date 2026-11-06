import { useCallback, useMemo, useState, type SetStateAction } from 'react'
import { useQuery } from '@tanstack/react-query'
import { libraryApi } from '../api/libraryApi'
import { PROCESSING_POLL_MS } from '../api/queries'
import type { ChunkLocation, DocumentChunk } from '../types'

export interface ViewerLocation {
  kind: ChunkLocation['kind']
  number: number
  /** Thứ tự đoạn trong trang hoặc slide, bắt đầu từ 1. */
  passage?: number
}

/** Giữ trang và độ phóng khi SidePanel đổi từ khung cạnh sang thẻ dialog toàn màn hình. */
const viewState = new Map<string, { page: number; zoom: number }>()

export function parseViewerLocation(params: URLSearchParams): ViewerLocation | null {
  const kind = params.get('kind')
  const number = Number(params.get('number'))
  const passage = Number(params.get('passage'))
  if (!['PAGE', 'SLIDE', 'IMAGE', 'PARAGRAPH'].includes(kind ?? '') || !Number.isSafeInteger(number) || number < 1) return null
  return { kind: kind as ViewerLocation['kind'], number, passage: Number.isSafeInteger(passage) && passage > 0 ? passage : undefined }
}

export function passageAt(chunks: DocumentChunk[], location: ViewerLocation | null) {
  if (!location) return null
  const matching = chunks.filter((chunk) => chunk.location.kind === location.kind &&
    chunk.location.number <= location.number && chunk.location.end >= location.number)
  return matching[(location.passage ?? 1) - 1] ?? matching[0] ?? null
}

export function useDocumentViewer(id: string, location: ViewerLocation | null) {
  const query = useQuery({
    queryKey: ['library', 'viewer', id],
    queryFn: () => libraryApi.viewer(id),
    staleTime: 0,
    retry: false,
    refetchInterval: (current) => current.state.data?.status === 'PENDING' || current.state.data?.status === 'PROCESSING' ? PROCESSING_POLL_MS : false,
  })
  const chunks = useMemo(() => query.data?.chunks ?? [], [query.data?.chunks])
  const target = useMemo(() => passageAt(chunks, location), [chunks, location])
  const [page, storePage] = useState(location?.number ?? viewState.get(id)?.page ?? 1)
  const [zoom, storeZoom] = useState(viewState.get(id)?.zoom ?? 1)
  const setPage = useCallback((next: SetStateAction<number>) => storePage((current) => {
    const value = typeof next === 'function' ? next(current) : next
    viewState.set(id, { page: value, zoom: viewState.get(id)?.zoom ?? 1 })
    return value
  }), [id])
  const setZoom = useCallback((next: SetStateAction<number>) => storeZoom((current) => {
    const value = typeof next === 'function' ? next(current) : next
    viewState.set(id, { page: viewState.get(id)?.page ?? page, zoom: value })
    return value
  }), [id, page])
  const maxPage = query.data?.processing.units ?? Math.max(1, ...chunks.map((chunk) => chunk.location.end))
  const turn = useCallback((delta: number) => setPage((current) => Math.min(Math.max(current + delta, 1), maxPage)), [maxPage, setPage])
  return { query, chunks, target, page, setPage, zoom, setZoom, maxPage, turn }
}
