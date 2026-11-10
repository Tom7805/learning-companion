import { createElement, useCallback, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { DocumentViewer } from '../components/viewer/DocumentViewer'
import { parseViewerLocation } from './useDocumentViewer'

const VIEWER_KEYS = ['viewer', 'kind', 'number', 'passage']

/**
 * Khung xem tài liệu ở khung bên, điều khiển bằng địa chỉ trang ({@code ?viewer=…&kind=PAGE&number=12&passage=3},
 * NCL-05-CN-005). Trang nào cần mở tài liệu tại chỗ (kho, không gian học) dùng hook này: tải lại trang hay bấm Quay lại
 * vẫn đúng tài liệu và đúng đoạn. {@code titleOf} cho tên tài liệu nếu trang đã có sẵn.
 */
export function useViewerPanel(titleOf: (id: string) => string | undefined) {
  const { t } = useTranslation()
  const panel = useSidePanel()
  const [params, setParams] = useSearchParams()
  const viewerId = params.get('viewer')
  const viewerKind = params.get('kind')
  const viewerNumber = params.get('number')
  const viewerPassage = params.get('passage')
  const title = viewerId ? (titleOf(viewerId) ?? t('library.content')) : ''

  const closeViewer = useCallback(
    () =>
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const key of VIEWER_KEYS) next.delete(key)
          return next
        },
        { replace: true },
      ),
    [setParams],
  )

  const openViewer = useCallback(
    (id: string) =>
      setParams((current) => {
        const next = new URLSearchParams(current)
        for (const key of VIEWER_KEYS) next.delete(key)
        next.set('viewer', id)
        return next
      }),
    [setParams],
  )

  useEffect(() => {
    if (!viewerId) {
      if (panel.content?.id.startsWith('viewer:')) panel.close()
      return
    }
    const contentId = `viewer:${viewerId}:${viewerKind}:${viewerNumber}:${viewerPassage}`
    if (panel.content?.id === contentId && panel.content.title === title) return
    const location = parseViewerLocation(
      new URLSearchParams({
        ...(viewerKind && { kind: viewerKind }),
        ...(viewerNumber && { number: viewerNumber }),
        ...(viewerPassage && { passage: viewerPassage }),
      }),
    )
    panel.open({
      id: contentId,
      title,
      render: () => createElement(DocumentViewer, { key: contentId, id: viewerId, location }),
      onClose: closeViewer,
    })
    // panel.content đổi khi đóng; thêm cả panel vào deps sẽ mở lại khung ngay trước lúc đổi trang.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewerId, viewerKind, viewerNumber, viewerPassage, panel.open, title, closeViewer])

  return { viewerId, openViewer, closeViewer }
}
