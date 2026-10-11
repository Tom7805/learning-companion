import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { documentPath, routes } from '@/app/routes'
import { sampleDocument } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { LibraryPage } from './LibraryPage'

function renderPage() {
  return renderWithProviders(<LibraryPage />, { path: routes.library })
}

describe('LibraryPage', () => {
  it('liệt kê tài liệu của người học kèm loại, dung lượng và lời nhắc quyền riêng tư', async () => {
    renderPage()

    const card = await screen.findByTestId('document-card')
    expect(card).toHaveAttribute('href', documentPath(sampleDocument.id))
    expect(within(card).getByText('Ghi chú: Định luật Ohm')).toBeInTheDocument()
    expect(within(card).getByText('Văn bản')).toBeInTheDocument()
    expect(within(card).getByText('1,5 KB')).toBeInTheDocument()
    expect(screen.getByTestId('library-summary')).toHaveTextContent('1 tài liệu · 1,5 KB')
    expect(screen.getByTestId('privacy-note')).toHaveTextContent('Quản trị viên chỉ thấy số lượng và dung lượng')
  })

  it('thư viện trống thì giải thích tài liệu sẽ nằm ở đâu', async () => {
    server.use(http.get('*/api/v1/library/documents', () => HttpResponse.json([])))
    renderPage()

    expect(await screen.findByTestId('library-empty')).toHaveTextContent('Thư viện của bạn đang trống')
    expect(screen.queryByTestId('library-summary')).not.toBeInTheDocument()
  })

  it('lỗi tải thì cho thử lại', async () => {
    let attempt = 0
    server.use(
      http.get('*/api/v1/library/documents', () => {
        attempt += 1
        return attempt === 1 ? HttpResponse.error() : HttpResponse.json([])
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByTestId('library-empty')).toBeInTheDocument()
  })
})
