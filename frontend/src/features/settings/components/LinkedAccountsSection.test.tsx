import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { LinkedAccountsSection } from './LinkedAccountsSection'

const googleIdentity = {
  id: 'lien-ket-1',
  provider: 'google',
  providerName: 'Google',
  email: 'lan.anh@gmail.com',
  linkedAt: '2026-10-12T03:00:00Z',
}

function renderSection(hasPassword: boolean, identities = [googleIdentity]) {
  server.use(
    http.get('*/api/v1/auth/session', () =>
      HttpResponse.json({ authenticated: true, account: { ...verifiedAccount, hasPassword } }),
    ),
    http.get('*/api/v1/auth/oauth/providers', () => HttpResponse.json([{ id: 'google', name: 'Google' }])),
    http.get('*/api/v1/accounts/:id/linked-identities', () => HttpResponse.json(identities)),
  )
  return renderWithProviders(<LinkedAccountsSection onNavigate={() => undefined} />, {
    path: '/',
    extraRoutes: [{ path: routes.security, element: <p>trang bảo mật</p> }],
  })
}

describe('LinkedAccountsSection', () => {
  it('hiện tài khoản Google đã liên kết kèm địa chỉ thư', async () => {
    renderSection(true)

    const row = await screen.findByTestId('linked-google')
    expect(row).toHaveTextContent('Đã liên kết với lan.anh@gmail.com')
    expect(within(row).getByRole('button', { name: 'Gỡ liên kết Google' })).toBeInTheDocument()
  })

  it('TC-04: tài khoản chưa có mật khẩu bấm gỡ liên kết thì được yêu cầu đặt mật khẩu trước', async () => {
    let deleted = false
    server.use(
      http.delete('*/api/v1/accounts/:id/linked-identities/:identityId', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderSection(false)

    await user.click(await screen.findByRole('button', { name: 'Gỡ liên kết Google' }))

    const dialog = await screen.findByRole('dialog', { name: 'Hãy đặt mật khẩu trước' })
    expect(dialog).toHaveTextContent('chỉ đăng nhập bằng Google')
    await user.click(within(dialog).getByRole('button', { name: 'Đặt mật khẩu' }))
    expect(await screen.findByText('trang bảo mật')).toBeInTheDocument()
    expect(deleted).toBe(false)
  })

  it('đã có mật khẩu thì xác nhận rồi gỡ liên kết', async () => {
    let deleted: string | undefined
    server.use(
      http.delete('*/api/v1/accounts/:id/linked-identities/:identityId', ({ params }) => {
        deleted = params.identityId as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderSection(true)

    await user.click(await screen.findByRole('button', { name: 'Gỡ liên kết Google' }))
    const dialog = await screen.findByRole('dialog', { name: 'Gỡ liên kết Google?' })
    await user.click(within(dialog).getByRole('button', { name: 'Gỡ liên kết' }))

    expect(await screen.findByTestId('linked-notice')).toHaveTextContent('Đã gỡ liên kết Google.')
    await waitFor(() => expect(deleted).toBe('lien-ket-1'))
  })

  it('chưa liên kết thì có nút liên kết', async () => {
    renderSection(true, [])

    const row = await screen.findByTestId('linked-google')
    expect(row).toHaveTextContent('Chưa liên kết')
    expect(within(row).getByRole('button', { name: 'Liên kết Google' })).toBeInTheDocument()
  })
})
