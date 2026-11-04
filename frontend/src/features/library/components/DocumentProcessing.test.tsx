import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { documentPath, routes } from '@/app/routes'
import { sampleDocument, sampleFolderTree } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { DocumentPage } from '../pages/DocumentPage'
import { LibraryPage } from '../pages/LibraryPage'
import type { DocumentCard, LearningDocument } from '../types'

/** NCL-05-CN-003 — trạng thái xử lý và lỗi của từng tài liệu trên giao diện. */

const scanned: DocumentCard = {
  ...sampleDocument,
  id: 'tai-lieu-quet',
  title: 'Bài giảng quét',
  type: 'PDF',
  status: 'FAILED',
  processing: { error: 'OCR_FAILED', errorPage: 12, retryable: true, chunks: 0, processedAt: '2026-11-04T03:00:00Z' },
}
const blank: DocumentCard = {
  ...sampleDocument,
  id: 'trang-trang',
  title: 'Trang trắng',
  type: 'PDF',
  status: 'FAILED',
  processing: { error: 'NO_TEXT', retryable: false, chunks: 0, processedAt: '2026-11-04T03:00:00Z' },
}

function renderLibrary(documents: DocumentCard[]) {
  server.use(
    http.get('*/api/v1/library/documents', () => HttpResponse.json(documents)),
    http.get('*/api/v1/library/folders', () =>
      HttpResponse.json({ ...sampleFolderTree, rootDocumentCount: documents.length, totalDocumentCount: documents.length }),
    ),
  )
  return renderWithProviders(<LibraryPage />, { path: routes.library })
}

function renderDocument(document: LearningDocument) {
  server.use(http.get('*/api/v1/library/documents/:id', () => HttpResponse.json(document)))
  return renderWithProviders(<DocumentPage />, {
    path: routes.libraryDocument,
    initialEntries: [documentPath(document.id)],
    extraRoutes: [{ path: routes.library, element: <p>thư viện</p> }],
  })
}

describe('Trạng thái xử lý tài liệu', () => {
  it('TC-03: lỗi nhận dạng chữ ở trang 12 hiện lý do và nút thử lại; bấm thì tài liệu quay về hàng chờ', async () => {
    let documents = [scanned]
    const reprocessed: string[] = []
    server.use(
      http.post('*/api/v1/library/documents/:id/reprocess', ({ params }) => {
        reprocessed.push(String(params.id))
        documents = [{ ...scanned, status: 'PENDING', processing: { retryable: false, chunks: 0 } }]
        return HttpResponse.json(documents[0])
      }),
    )
    server.use(http.get('*/api/v1/library/documents', () => HttpResponse.json(documents)))
    const { user } = renderLibrary(documents)
    server.use(http.get('*/api/v1/library/documents', () => HttpResponse.json(documents)))

    const item = (await screen.findAllByTestId('document-item'))[0]
    expect(within(item).getByTestId('document-status')).toHaveTextContent('Xử lý lỗi')
    expect(within(item).getByTestId('processing-error')).toHaveTextContent(
      'Dịch vụ nhận dạng chữ gặp lỗi ở trang 12. Hãy thử lại sau ít phút.',
    )
    await user.click(within(item).getByRole('button', { name: 'Thử lại xử lý “Bài giảng quét”' }))

    await waitFor(() => expect(reprocessed).toEqual(['tai-lieu-quet']))
    expect(await screen.findByText('Đã đưa “Bài giảng quét” vào hàng chờ xử lý lại.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('document-status')).toHaveTextContent('Chờ xử lý'))
    expect(screen.queryByTestId('processing-error')).not.toBeInTheDocument()
  })

  it('TC-04: tệp không có chữ gợi ý tải bản rõ hơn và không có nút thử lại', async () => {
    renderLibrary([blank])

    const item = (await screen.findAllByTestId('document-item'))[0]
    expect(within(item).getByTestId('processing-error')).toHaveTextContent(
      'Không trích được văn bản nào từ tài liệu này. Hãy tải lên bản rõ hơn hoặc bản có chữ chọn được.',
    )
    expect(within(item).queryByTestId('processing-retry')).not.toBeInTheDocument()
  })

  it('trang tài liệu đang xử lý báo đang trích văn bản', async () => {
    renderDocument({ ...sampleDocument, contentText: '', status: 'PROCESSING', processing: { retryable: false, chunks: 0 } })

    expect(await screen.findByTestId('processing-state')).toHaveTextContent('Đang trích văn bản, nhận dạng chữ và lập chỉ mục')
    expect(screen.getByTestId('document-status')).toHaveTextContent('Đang xử lý')
  })

  it('trang tài liệu lỗi nêu lý do; lỗi do tệp thì gợi ý tải bản khác', async () => {
    renderDocument({ ...sampleDocument, ...blank, contentText: '', mimeType: 'application/pdf', updatedAt: blank.createdAt })

    const state = await screen.findByTestId('processing-state')
    expect(state).toHaveTextContent('Chưa xử lý được tài liệu này')
    expect(state).toHaveTextContent('Hãy tải lên bản khác của tài liệu.')
    expect(within(state).queryByRole('button')).not.toBeInTheDocument()
  })

  it('tài liệu sẵn sàng tóm tắt số trang và số đoạn đã lập chỉ mục', async () => {
    renderDocument({
      ...sampleDocument,
      type: 'PDF',
      status: 'READY',
      processing: { retryable: false, units: 50, chunks: 64, processedAt: '2026-11-04T03:00:00Z' },
    })

    expect(await screen.findByTestId('processing-summary')).toHaveTextContent('50 trang · 64 đoạn đã lập chỉ mục')
  })
})
