import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reloadPage } from '@/shared/lib/navigation'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ErrorPage } from './ErrorPage'

vi.mock('@/shared/lib/navigation', () => ({ reloadPage: vi.fn() }))

function Broken({ error }: { error: Error }): never {
  throw error
}

function renderBroken(error: Error) {
  return renderWithProviders(null, {
    routes: [{ path: '/', element: <Broken error={error} />, errorElement: <ErrorPage /> }],
  })
}

describe('ErrorPage', () => {
  beforeEach(() => {
    // React và bộ định tuyến ghi lỗi ra console; ở đây lỗi là cố ý.
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    vi.spyOn(console, 'warn').mockImplementation(() => undefined)
  })

  it('không tải được mã của trang vì mạng thì nói rõ và cho tải lại', async () => {
    const { user } = renderBroken(new TypeError('Failed to fetch dynamically imported module: /assets/LibraryPage.js'))

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Không mở được trang này')
    expect(alert).toHaveTextContent('Không kết nối được tới máy chủ')
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(reloadPage).toHaveBeenCalledOnce()
  })

  it('lỗi bất ngờ khác thì báo hệ thống đang gặp sự cố', () => {
    renderBroken(new Error('undefined is not a function'))

    expect(screen.getByRole('alert')).toHaveTextContent('Hệ thống đang gặp sự cố')
  })
})
