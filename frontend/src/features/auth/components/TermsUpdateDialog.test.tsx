import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { apiError, privacyStatus } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { TermsUpdateDialog } from './TermsUpdateDialog'

const updated = {
  ...privacyStatus,
  acceptanceRequired: true,
  terms: {
    ...privacyStatus.terms,
    version: '2026-12-01',
    changes: ['Thêm điều khoản về trợ lý học tập.', 'Làm rõ quyền xóa dữ liệu.'],
  },
}

describe('TermsUpdateDialog', () => {
  it('TC-03: nêu các điểm thay đổi của văn bản vừa cập nhật và gửi đúng phiên bản khi đồng ý', async () => {
    let accepted: unknown
    server.use(
      http.post('*/api/v1/accounts/:id/terms/accept', async ({ request }) => {
        accepted = await request.json()
        return HttpResponse.json({ ...updated, acceptanceRequired: false })
      }),
    )
    const { user } = renderWithProviders(<TermsUpdateDialog status={updated} onLogout={() => undefined} />)

    expect(screen.getByRole('heading', { name: 'Điều khoản đã được cập nhật' })).toBeInTheDocument()
    expect(screen.getByTestId('terms-update-terms')).toHaveTextContent('Thêm điều khoản về trợ lý học tập.')
    expect(screen.getByTestId('terms-update-terms')).toHaveTextContent('Phiên bản 2026-12-01')
    // Chính sách không đổi phiên bản thì không cần nêu.
    expect(screen.queryByTestId('terms-update-privacy')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Đồng ý và tiếp tục' }))
    await waitFor(() => expect(accepted).toEqual({ termsVersion: '2026-12-01', privacyVersion: '2026-10-01' }))
  })

  it('không đóng được bằng Esc; đăng xuất được nếu chưa muốn đồng ý', async () => {
    const onLogout = vi.fn()
    const { user } = renderWithProviders(<TermsUpdateDialog status={updated} onLogout={onLogout} />)

    await user.keyboard('{Escape}')
    expect(screen.getByTestId('terms-update-dialog')).toHaveAttribute('open')
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    expect(onLogout).toHaveBeenCalledOnce()
  })

  it('bản mới hơn vừa ra trong lúc đọc thì báo để đọc lại', async () => {
    server.use(http.post('*/api/v1/accounts/:id/terms/accept', () => apiError(409, 'TERMS_VERSION_OUTDATED')))
    const { user } = renderWithProviders(<TermsUpdateDialog status={updated} onLogout={() => undefined} />)

    await user.click(screen.getByRole('button', { name: 'Đồng ý và tiếp tục' }))

    expect(await screen.findByText(/Điều khoản vừa được cập nhật/)).toBeInTheDocument()
  })
})
