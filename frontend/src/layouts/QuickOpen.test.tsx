import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { documentPath, routes } from '@/app/routes'
import { sampleDocument } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { QuickOpen } from './QuickOpen'

const physics = { ...sampleDocument, id: '5d1c0e6a-2222-4b1a-8c3e-000000000002', title: 'Bài tập Vật lý 11' }

function renderQuickOpen() {
  server.use(
    http.get('*/api/v1/library/documents', () =>
      HttpResponse.json(
        [sampleDocument, physics].map(({ id, title, type, originalFilename, sizeBytes, createdAt }) => ({
          id,
          title,
          type,
          originalFilename,
          sizeBytes,
          createdAt,
        })),
      ),
    ),
  )
  const onClose = vi.fn()
  const utils = renderWithProviders(<QuickOpen open onClose={onClose} />, {
    path: routes.home,
    extraRoutes: [
      { path: routes.library, element: <p>Trang thư viện</p> },
      { path: '/library/:id', element: <p>Trang tài liệu</p> },
    ],
  })
  return { ...utils, onClose }
}

const options = () => screen.getAllByRole('option').map((option) => option.textContent)

describe('QuickOpen', () => {
  it('mở ra là gõ được ngay, liệt kê trang và tài liệu theo nhóm', async () => {
    renderQuickOpen()

    const input = screen.getByRole('combobox', { name: 'Tìm trang hoặc tài liệu' })
    expect(input).toHaveFocus()
    await waitFor(() => expect(options()).toHaveLength(4))
    expect(options()).toEqual(['Trang chính', 'Thư viện', 'Ghi chú: Định luật Ohm', 'Bài tập Vật lý 11'])
    const listbox = screen.getByRole('listbox')
    expect(within(listbox).getByText('Trang')).toBeInTheDocument()
    expect(within(listbox).getByText('Tài liệu')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-activedescendant', screen.getAllByRole('option')[0].id)
  })

  it('gõ không dấu vẫn tìm được, mũi tên chọn và Enter mở tài liệu', async () => {
    const { user, router, onClose } = renderQuickOpen()
    await waitFor(() => expect(options()).toHaveLength(4))

    await user.keyboard('vat ly')
    expect(options()).toEqual(['Bài tập Vật lý 11'])

    await user.clear(screen.getByRole('combobox'))
    await user.keyboard('dinh luat')
    expect(options()).toEqual(['Ghi chú: Định luật Ohm'])
    expect(screen.getByRole('option')).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Enter}')

    expect(onClose).toHaveBeenCalledOnce()
    expect(router.state.location.pathname).toBe(documentPath(sampleDocument.id))
  })

  it('phím mũi tên đi vòng qua kết quả; bấm chuột vào một trang thì mở trang đó', async () => {
    const { user, router } = renderQuickOpen()
    await waitFor(() => expect(options()).toHaveLength(4))

    await user.keyboard('{ArrowUp}')
    expect(screen.getAllByRole('option')[3]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowDown}{ArrowDown}')
    expect(screen.getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true')

    await user.click(screen.getByRole('option', { name: 'Thư viện' }))
    expect(router.state.location.pathname).toBe(routes.library)
  })

  it('không khớp gì thì nói rõ', async () => {
    const { user } = renderQuickOpen()

    await user.keyboard('hóa học hữu cơ')

    expect(screen.queryAllByRole('option')).toHaveLength(0)
    expect(screen.getByRole('status')).toHaveTextContent('Không thấy trang hay tài liệu nào khớp.')
    await user.keyboard('{Enter}')
  })

  it('Esc hoặc bấm ra ngoài hộp thì đóng', async () => {
    const { onClose } = renderQuickOpen()
    const dialog = screen.getByTestId('quick-open')

    fireEvent(dialog, new Event('cancel', { cancelable: true }))
    fireEvent.click(dialog)
    fireEvent.click(screen.getByRole('combobox'))

    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
