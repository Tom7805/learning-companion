import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { documentPath, routes } from '@/app/routes'
import { apiError, sampleDocument } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { DocumentPage } from './DocumentPage'

function renderPage(id = sampleDocument.id) {
  return renderWithProviders(<DocumentPage />, {
    path: routes.libraryDocument,
    initialEntries: [documentPath(id)],
    extraRoutes: [{ path: routes.library, element: <p>thư viện</p> }],
  })
}

describe('DocumentPage', () => {
  it('TC-01: mở tài liệu của mình thì thấy đủ nội dung', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Ghi chú: Định luật Ohm' })).toBeInTheDocument()
    expect(screen.getByTestId('document-content')).toHaveTextContent('I = U / R')
    expect(screen.getByText('Chỉ mình bạn xem được')).toBeInTheDocument()
    expect(screen.getByTestId('original-filename')).toHaveTextContent('dinh-luat-ohm.txt · 1,5 KB')
  })

  it('TC-04: tài liệu của người khác hiện y như tài liệu không tồn tại', async () => {
    renderPage('ma-cua-nguoi-khac')

    expect(await screen.findByTestId('document-not-found')).toHaveTextContent('Không tìm thấy tài liệu')
    expect(screen.getByRole('link', { name: 'Về thư viện' })).toHaveAttribute('href', routes.library)
  })

  it('xem trước tệp gốc qua đường dẫn ký', async () => {
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Xem tệp gốc' }))

    expect(await screen.findByTestId('file-preview')).toHaveTextContent('I = U / R')
    expect(screen.getByRole('button', { name: 'Tải lại tệp' })).toBeInTheDocument()
    expect(screen.queryByTestId('link-renewed')).not.toBeInTheDocument()
  })

  it('TC-02: kho tệp báo đường dẫn hết hạn thì giao diện tự xin đường dẫn mới và mở lại', async () => {
    let links = 0
    const opened: string[] = []
    server.use(
      http.post('*/api/v1/library/documents/:id/file-link', () => {
        links += 1
        // Máy chủ coi là đã hết hạn dù đồng hồ máy người học còn thấy hạn.
        return HttpResponse.json({ url: `/api/v1/files/link-${links}`, expiresAt: new Date(Date.now() + 300_000).toISOString() })
      }),
      http.get('*/api/v1/files/:token', ({ params }) => {
        opened.push(params.token as string)
        return params.token === 'link-1'
          ? apiError(410, 'FILE_LINK_EXPIRED')
          : new HttpResponse(sampleDocument.contentText, { headers: { 'Content-Type': 'text/plain' } })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Xem tệp gốc' }))

    expect(await screen.findByTestId('file-preview')).toHaveTextContent('I = U / R')
    expect(screen.getByTestId('link-renewed')).toHaveTextContent('hệ thống đã tự lấy đường dẫn mới')
    expect(opened).toEqual(['link-1', 'link-2'])
    expect(links).toBe(2)
  })

  it('đường dẫn còn hạn thì dùng lại, không xin thêm', async () => {
    let links = 0
    server.use(
      http.post('*/api/v1/library/documents/:id/file-link', () => {
        links += 1
        return HttpResponse.json({ url: '/api/v1/files/link-1', expiresAt: new Date(Date.now() + 300_000).toISOString() })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Xem tệp gốc' }))
    await screen.findByTestId('file-preview')
    await user.click(screen.getByRole('button', { name: 'Tải lại tệp' }))
    await screen.findByTestId('file-preview')

    expect(links).toBe(1)
  })

  it('không mở được tệp thì báo lỗi và vẫn giữ nút thử lại', async () => {
    server.use(http.get('*/api/v1/files/:token', () => apiError(500, 'INTERNAL_ERROR')))
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Xem tệp gốc' }))

    expect(await screen.findByTestId('file-error')).toHaveTextContent('Chưa mở được tệp gốc')
    expect(screen.getByRole('button', { name: 'Xem tệp gốc' })).toBeEnabled()
  })
})
