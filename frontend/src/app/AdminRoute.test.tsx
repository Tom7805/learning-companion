import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { adminIdentity, apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AdminRoute } from './AdminRoute'

function renderAdmin() {
  return renderWithProviders(null, {
    initialEntries: ['/admin/audit'],
    routes: [
      { path: '/', element: <p>Trang chính</p> },
      { path: '/admin', element: <AdminRoute />, children: [{ path: '/admin/audit', element: <p>Trang nhật ký</p> }] },
    ],
  })
}

describe('AdminRoute', () => {
  it('TC-05: người học mở thẳng đường dẫn quản trị thì bị từ chối, không thấy trang quản trị', async () => {
    let asked = 0
    server.use(
      http.get('*/api/v1/admin/me', () => {
        asked += 1
        return apiError(403, 'FORBIDDEN')
      }),
    )
    const { user } = renderAdmin()

    expect(await screen.findByRole('heading', { name: 'Trang này chỉ dành cho quản trị viên' })).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden')).toHaveTextContent('đã được ghi vào nhật ký')
    expect(screen.queryByText('Trang nhật ký')).not.toBeInTheDocument()
    // Không hỏi lại: mỗi lần hỏi là một dòng từ chối trong nhật ký.
    expect(asked).toBe(1)

    await user.click(screen.getByRole('link', { name: 'Về trang chính' }))
    expect(await screen.findByText('Trang chính')).toBeInTheDocument()
  })

  it('quản trị viên vào được trang con', async () => {
    server.use(http.get('*/api/v1/admin/me', () => HttpResponse.json(adminIdentity)))
    renderAdmin()

    expect(await screen.findByText('Trang nhật ký')).toBeInTheDocument()
  })

  it('lỗi khác thì báo lỗi và cho thử lại', async () => {
    let fail = true
    server.use(
      http.get('*/api/v1/admin/me', () => (fail ? apiError(500, 'INTERNAL_ERROR') : HttpResponse.json(adminIdentity))),
    )
    const { user } = renderAdmin()

    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    fail = false
    await user.click(retry)
    expect(await screen.findByText('Trang nhật ký')).toBeInTheDocument()
  })
})
