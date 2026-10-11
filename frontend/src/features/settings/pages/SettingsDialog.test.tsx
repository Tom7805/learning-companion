import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { normalizeTimeZone } from '@/shared/lib/timezone'
import { apiError, sampleProfile } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ImageFileError, loadImageFile } from '../lib/image'
import { SettingsDialog } from './SettingsDialog'

vi.mock('../lib/image', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../lib/image')>()),
  loadImageFile: vi.fn(),
}))

/** Ghi lại mọi lần lưu hồ sơ để kiểm đã gửi gì, hoặc không gửi gì. */
let patches: Record<string, unknown>[] = []

function renderDialog(profile = sampleProfile) {
  server.use(
    http.get('*/api/v1/profiles/me', () => HttpResponse.json(profile)),
    http.patch('*/api/v1/profiles/:id', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      patches.push(body)
      return HttpResponse.json({ ...profile, ...body })
    }),
  )
  return renderWithProviders(<SettingsDialog open onClose={() => undefined} />)
}

describe('SettingsDialog · hồ sơ', () => {
  beforeEach(() => {
    patches = []
  })

  it('TC-01: chọn múi giờ Việt Nam và mục tiêu ba mươi phút thì lưu ngay từng thay đổi', async () => {
    const { user } = renderDialog()

    const zone = await screen.findByLabelText('Múi giờ')
    await user.selectOptions(zone, 'Asia/Ho_Chi_Minh')
    await waitFor(() => expect(patches).toContainEqual({ timeZone: 'Asia/Ho_Chi_Minh' }))
    expect(await screen.findByTestId('time-zone-notice')).toHaveTextContent('Hồ Chí Minh (Việt Nam)')

    await user.click(screen.getByRole('button', { name: '30 phút', pressed: false }))
    await waitFor(() => expect(patches).toContainEqual({ dailyGoalMinutes: 30 }))
    expect(await screen.findByTestId('save-status')).toHaveTextContent('Đã lưu')
    expect(screen.getByRole('button', { name: '30 phút' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('TC-02: tên hiển thị sáu mươi ký tự báo tối đa năm mươi ký tự và không lưu', async () => {
    const { user } = renderDialog()
    const name = await screen.findByLabelText('Tên hiển thị')

    await user.clear(name)
    await user.type(name, 'A'.repeat(60))
    await user.tab()

    expect(screen.getByText('Tên hiển thị tối đa 50 ký tự.')).toBeInTheDocument()
    expect(screen.getByTestId('name-count')).toHaveTextContent('60/50 ký tự')
    expect(name).toHaveAttribute('aria-invalid', 'true')
    expect(patches).toEqual([])
  })

  it('tên hợp lệ được lưu khi rời ô, khoảng trắng thừa được gộp', async () => {
    const { user } = renderDialog()
    const name = await screen.findByLabelText('Tên hiển thị')

    await user.clear(name)
    await user.type(name, '  Nguyễn   Lan Anh ')
    await user.tab()

    await waitFor(() => expect(patches).toEqual([{ displayName: 'Nguyễn Lan Anh' }]))
  })

  it('TC-03: chọn tệp không phải ảnh làm ảnh đại diện thì báo các định dạng được nhận', async () => {
    vi.mocked(loadImageFile).mockRejectedValueOnce(new ImageFileError('format'))
    const { user } = renderDialog()

    const fake = new File(['ghi chú học tập'], 'anh.png', { type: 'image/png' })
    await user.upload(await screen.findByTestId('avatar-input'), fake)

    expect(await screen.findByTestId('avatar-error')).toHaveTextContent(
      'Ảnh đại diện cần là tệp ảnh JPG, PNG hoặc WebP.',
    )
    expect(screen.queryByRole('dialog', { name: 'Cắt ảnh đại diện' })).not.toBeInTheDocument()
  })

  it('ảnh quá 5 MB bị từ chối trước khi gửi', async () => {
    vi.mocked(loadImageFile).mockRejectedValueOnce(new ImageFileError('size'))
    const { user } = renderDialog()

    await user.upload(await screen.findByTestId('avatar-input'), new File(['x'], 'lon.jpg', { type: 'image/jpeg' }))

    expect(await screen.findByTestId('avatar-error')).toHaveTextContent('Ảnh tối đa 5 MB')
  })

  it('TC-04: đổi từ giờ Việt Nam sang múi giờ chậm hơn bảy giờ thì báo các mốc sẽ tính theo múi giờ mới', async () => {
    const { user } = renderDialog({ ...sampleProfile, timeZone: 'Asia/Ho_Chi_Minh' })

    await user.selectOptions(await screen.findByLabelText('Múi giờ'), 'Africa/Abidjan')

    const notice = await screen.findByTestId('time-zone-notice')
    expect(notice).toHaveTextContent('Từ giờ mọi mốc thời gian hiển thị theo múi giờ (GMT+00:00) Abidjan.')
    expect(notice).toHaveTextContent('Lịch học và lời nhắc được tính lại theo múi giờ mới.')
    expect(patches).toEqual([{ timeZone: 'Africa/Abidjan' }])
  })

  it('gợi ý múi giờ của thiết bị khi hồ sơ chưa chọn, bấm một lần là dùng', async () => {
    const device = normalizeTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone)
    const { user } = renderDialog()

    const suggestion = await screen.findByTestId('time-zone-suggestion')
    await user.click(within(suggestion).getByRole('button', { name: 'Dùng múi giờ này' }))

    await waitFor(() => expect(patches).toEqual([{ timeZone: device }]))
  })

  it('mục tiêu tùy chỉnh ngoài khoảng cho phép bị báo lỗi và không lưu', async () => {
    const { user } = renderDialog()
    const custom = await screen.findByLabelText('Số phút tùy chọn')

    await user.clear(custom)
    await user.type(custom, '900{Enter}')

    expect(screen.getByText('Mục tiêu từ 5 đến 600 phút mỗi ngày.')).toBeInTheDocument()
    expect(patches).toEqual([])

    await user.clear(custom)
    await user.type(custom, '25{Enter}')
    await waitFor(() => expect(patches).toEqual([{ dailyGoalMinutes: 25 }]))
  })

  it('đổi ngôn ngữ trả lời của trợ lý và mốc đổi ngày học', async () => {
    const { user } = renderDialog()

    await user.click(await screen.findByRole('radio', { name: 'English' }))
    await user.selectOptions(screen.getByTestId('rollover'), '5')

    await waitFor(() => expect(patches).toEqual([{ assistantLanguage: 'en' }, { dayRolloverHour: 5 }]))
  })

  it('máy chủ từ chối thì báo dưới ô và trạng thái lưu báo lỗi', async () => {
    server.use(http.patch('*/api/v1/profiles/:id', () => apiError(400, 'INVALID_TIME_ZONE', 'timeZone')))
    renderWithProviders(<SettingsDialog open onClose={() => undefined} />)
    const { user } = { user: (await import('@testing-library/user-event')).default.setup() }

    await user.selectOptions(await screen.findByLabelText('Múi giờ'), 'Asia/Tokyo')

    expect(await screen.findByText('Múi giờ này không hợp lệ, hãy chọn trong danh sách.')).toBeInTheDocument()
    expect(screen.getByTestId('save-status')).toHaveTextContent('Chưa lưu được')
  })
})
