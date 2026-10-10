import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { RegisterForm } from './RegisterForm'

const STRONG = 'mùa thu hà nội 2026'

function setup(onRegistered = vi.fn()) {
  const utils = renderWithProviders(<RegisterForm onRegistered={onRegistered} />)
  const fields = {
    displayName: () => screen.getByLabelText('Tên hiển thị'),
    email: () => screen.getByLabelText('Thư điện tử'),
    password: () => screen.getByLabelText('Mật khẩu', { exact: true }),
    confirm: () => screen.getByLabelText('Nhập lại mật khẩu'),
    terms: () => screen.getByRole('checkbox'),
    submit: () => screen.getByRole('button', { name: /Tạo tài khoản/ }),
  }
  const fillValid = async (overrides: Partial<Record<'displayName' | 'email' | 'password' | 'confirm', string>> = {}) => {
    await utils.user.type(fields.displayName(), overrides.displayName ?? 'Lan Anh')
    await utils.user.type(fields.email(), overrides.email ?? 'Lan.Anh@Example.com')
    await utils.user.type(fields.password(), overrides.password ?? STRONG)
    await utils.user.type(fields.confirm(), overrides.confirm ?? overrides.password ?? STRONG)
    await utils.user.click(fields.terms())
  }
  return { ...utils, fields, fillValid, onRegistered }
}

describe('RegisterForm', () => {
  it('gửi biểu mẫu trống thì báo lỗi từng trường, tóm tắt số lỗi và đưa tiêu điểm về trường đầu', async () => {
    const { user, fields } = setup()
    await screen.findByTestId('terms-version')

    await user.click(fields.submit())

    expect(await screen.findByTestId('error-summary')).toHaveTextContent('Biểu mẫu còn 5 chỗ cần sửa.')
    expect(screen.getByText('Hãy nhập tên hiển thị.')).toBeInTheDocument()
    expect(screen.getByText('Hãy nhập địa chỉ thư điện tử.')).toBeInTheDocument()
    expect(screen.getByText('Hãy nhập mật khẩu.')).toBeInTheDocument()
    expect(screen.getByText('Hãy nhập lại mật khẩu.')).toBeInTheDocument()
    expect(screen.getByText(/Bạn cần đồng ý điều khoản/)).toBeInTheDocument()
    expect(fields.displayName()).toHaveFocus()
    expect(fields.displayName()).toHaveAttribute('aria-invalid', 'true')
    expect(fields.displayName()).toHaveAccessibleDescription(/Hãy nhập tên hiển thị\./)
  })

  it('kiểm tra định dạng thư, độ dài mật khẩu và nhập lại khi rời ô', async () => {
    const { user, fields } = setup()

    await user.type(fields.email(), 'khong-phai-email')
    await user.tab()
    expect(await screen.findByText(/Địa chỉ thư chưa đúng định dạng/)).toBeInTheDocument()

    await user.type(fields.password(), 'ngan')
    await user.tab()
    expect(await screen.findByText('Mật khẩu cần dài ít nhất 10 ký tự.')).toBeInTheDocument()

    await user.clear(fields.password())
    await user.type(fields.password(), STRONG)
    await user.type(fields.confirm(), 'mùa thu hà nội 2025')
    await user.tab()
    expect(await screen.findByText('Hai mật khẩu chưa khớp nhau.')).toBeInTheDocument()
  })

  it('tên hiển thị quá 50 ký tự bị báo lỗi', async () => {
    const { user, fields } = setup()
    await user.type(fields.displayName(), 'a'.repeat(51))
    await user.tab()
    expect(await screen.findByText('Tên hiển thị tối đa 50 ký tự.')).toBeInTheDocument()
  })

  it('đăng ký thành công gửi đúng dữ liệu kèm phiên bản điều khoản rồi báo cho trang', async () => {
    let payload: Record<string, unknown> | undefined
    server.use(
      http.post('*/api/v1/auth/register', async ({ request }) => {
        payload = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ email: 'lan.anh@example.com', resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user, fields, fillValid, onRegistered } = setup()
    await screen.findByTestId('terms-version')
    await fillValid({ displayName: '  Lan Anh  ' })

    await user.click(fields.submit())

    await waitFor(() => expect(onRegistered).toHaveBeenCalledTimes(1))
    expect(onRegistered).toHaveBeenCalledWith({ email: 'lan.anh@example.com', resendAvailableInSeconds: 60 }, 'Lan Anh')
    expect(payload).toEqual({
      displayName: 'Lan Anh',
      email: 'Lan.Anh@Example.com',
      password: STRONG,
      acceptTerms: true,
      termsVersion: '2026-10-01',
      privacyVersion: '2026-10-01',
    })
    expect(payload).not.toHaveProperty('confirmPassword')
  })

  it('hiện dấu tích hợp lệ cho trường đã điền đúng', async () => {
    const { user, fields } = setup()
    await user.type(fields.email(), 'lan.anh@example.com')
    await user.tab()
    expect(await screen.findByRole('img', { name: 'Hợp lệ' })).toBeInTheDocument()
  })

  it('mật khẩu đã lộ: báo lỗi ngay dưới ô mật khẩu, gợi ý cụm từ dài hơn và đưa tiêu điểm về ô đó', async () => {
    server.use(http.post('*/api/v1/auth/register', () => apiError(400, 'PASSWORD_BREACHED', 'password')))
    const { user, fields, fillValid, onRegistered } = setup()
    await screen.findByTestId('terms-version')
    await fillValid({ password: 'password1234' })

    await user.click(fields.submit())

    expect(await screen.findByText(/đã từng bị lộ trong các vụ rò rỉ dữ liệu/)).toBeInTheDocument()
    expect(fields.password()).toHaveAccessibleDescription(/cụm từ dài hơn/)
    expect(fields.password()).toHaveFocus()
    expect(screen.getByText('Không phải mật khẩu phổ biến đã bị lộ').closest('li')).toHaveAttribute('data-state', 'fail')
    expect(onRegistered).not.toHaveBeenCalled()

    // Đổi mật khẩu khác thì lỗi và trạng thái "đã lộ" biến mất.
    await user.clear(fields.password())
    await user.type(fields.password(), STRONG)
    await waitFor(() => expect(screen.queryByText(/đã từng bị lộ/)).not.toBeInTheDocument())
    expect(screen.getByText('Không phải mật khẩu phổ biến đã bị lộ').closest('li')).toHaveAttribute('data-state', 'pending')
  })

  it('điều khoản vừa đổi phiên bản: bỏ đánh dấu và yêu cầu đồng ý lại', async () => {
    server.use(http.post('*/api/v1/auth/register', () => apiError(409, 'TERMS_VERSION_OUTDATED', 'acceptTerms')))
    const { user, fields, fillValid } = setup()
    await screen.findByTestId('terms-version')
    await fillValid()

    await user.click(fields.submit())

    expect(await screen.findByText(/Điều khoản vừa được cập nhật/)).toBeInTheDocument()
    expect(fields.terms()).not.toBeChecked()
  })

  it('lỗi mạng hiện thông báo chung, giữ nguyên dữ liệu đã nhập', async () => {
    server.use(http.post('*/api/v1/auth/register', () => HttpResponse.error()))
    const { user, fields, fillValid } = setup()
    await screen.findByTestId('terms-version')
    await fillValid()

    await user.click(fields.submit())

    expect(await screen.findByTestId('form-error')).toHaveTextContent('Không kết nối được tới máy chủ')
    expect(fields.email()).toHaveValue('Lan.Anh@Example.com')
  })

  it('quá nhiều lần thử hiện thông báo chờ và mã tra cứu', async () => {
    server.use(http.post('*/api/v1/auth/register', () => apiError(429, 'RATE_LIMITED')))
    const { user, fields, fillValid } = setup()
    await screen.findByTestId('terms-version')
    await fillValid()

    await user.click(fields.submit())

    const alert = await screen.findByTestId('form-error')
    expect(alert).toHaveTextContent('Bạn thao tác quá nhanh')
    expect(alert).toHaveTextContent('req-test-1')
  })

  it('khóa biểu mẫu và hiện trạng thái đang gửi trong lúc chờ máy chủ', async () => {
    let release: () => void = () => undefined
    server.use(
      http.post('*/api/v1/auth/register', async () => {
        await new Promise<void>((resolve) => (release = resolve))
        return HttpResponse.json({ email: 'lan.anh@example.com', resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user, fields, fillValid, onRegistered } = setup()
    await screen.findByTestId('terms-version')
    await fillValid()

    await user.click(fields.submit())

    const busy = await screen.findByRole('button', { name: /Đang tạo tài khoản/ })
    expect(busy).toBeDisabled()
    expect(fields.email()).toBeDisabled()
    release()
    await waitFor(() => expect(onRegistered).toHaveBeenCalled())
  })

  it('nút con mắt hiện và ẩn cả hai ô mật khẩu', async () => {
    const { user, fields } = setup()
    expect(fields.password()).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }))

    expect(fields.password()).toHaveAttribute('type', 'text')
    expect(fields.confirm()).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ẩn mật khẩu' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('liên kết điều khoản và chính sách mở thẻ mới', async () => {
    setup()
    const terms = screen.getByRole('link', { name: /Điều khoản sử dụng/ })
    const privacy = screen.getByRole('link', { name: /Chính sách quyền riêng tư/ })
    expect(terms).toHaveAttribute('href', '/legal/terms')
    expect(terms).toHaveAttribute('target', '_blank')
    expect(privacy).toHaveAttribute('href', '/legal/privacy')
  })
})
