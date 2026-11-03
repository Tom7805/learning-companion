import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { setDisplayTimeZone } from '@/shared/lib/timezone'
import { apiError, sampleAuditLogs } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { AuditLogEntry, AuditLogPage as Page } from '../types'
import { AuditLogPage } from './AuditLogPage'

const AUDIT_URL = '*/api/v1/admin/audit-logs'

/** Ghi lại tham số của từng lần tra cứu gửi lên máy chủ. */
function captureSearches(respond: (params: URLSearchParams) => Page | Response = () => sampleAuditLogs) {
  const searches: URLSearchParams[] = []
  server.use(
    http.get(AUDIT_URL, ({ request }) => {
      const params = new URL(request.url).searchParams
      searches.push(params)
      const page = respond(params)
      return page instanceof Response ? page : HttpResponse.json(page)
    }),
  )
  return searches
}

function renderPage(search = '') {
  return renderWithProviders(<AuditLogPage />, { path: '/admin/audit', initialEntries: [`/admin/audit${search}`] })
}

const rows = () => screen.getAllByTestId('audit-row')

afterEach(() => setDisplayTimeZone(undefined))

describe('AuditLogPage', () => {
  it('TC-01: hiện từng lần đăng nhập kèm thời điểm, thiết bị, địa chỉ IP và kết quả', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeInTheDocument()
    await waitFor(() => expect(rows()).toHaveLength(4))
    expect(screen.getByTestId('audit-count')).toHaveTextContent('4 dòng')
    const [success, ...failures] = rows()
    expect(success).toHaveTextContent('lan.anh@example.com')
    expect(success).toHaveTextContent('Đăng nhập thành công')
    expect(within(success).getByTestId('audit-outcome')).toHaveTextContent('Thành công')
    for (const row of failures) {
      expect(row).toHaveTextContent('Chưa đăng nhập')
      expect(row).toHaveTextContent('Đăng nhập thất bại')
      expect(within(row).getByTestId('audit-outcome')).toHaveTextContent('Thất bại')
      // Lần sai chưa có người đăng nhập: tài khoản bị nhập sai hiện ở cột đối tượng.
      expect(row).toHaveTextContent('Tài khoản')
      expect(row).toHaveTextContent('lan.anh@example.com')
    }
    for (const row of rows()) {
      expect(row).toHaveTextContent('Chrome · Windows')
      expect(row).toHaveTextContent('203.0.113.7')
      expect(row.querySelector('time')).toHaveAttribute('dateTime')
    }
  })

  it('lọc theo tài khoản, nhóm thao tác và khoảng ngày tính trọn ngày theo múi giờ hiển thị', async () => {
    setDisplayTimeZone('Asia/Ho_Chi_Minh')
    const searches = captureSearches()
    const { user, router } = renderPage()
    await waitFor(() => expect(searches).toHaveLength(1))

    await user.type(screen.getByLabelText('Tài khoản'), '  lan.anh@example.com ')
    await user.selectOptions(screen.getByLabelText('Nhóm thao tác'), 'LOGIN')
    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-14')
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-14')
    await user.click(screen.getByRole('button', { name: 'Tra cứu' }))

    await waitFor(() => expect(searches).toHaveLength(2))
    const sent = searches[1]
    expect(sent.get('account')).toBe('lan.anh@example.com')
    expect(sent.get('category')).toBe('LOGIN')
    expect(sent.get('from')).toBe('2026-10-13T17:00:00.000Z')
    expect(sent.get('to')).toBe('2026-10-14T17:00:00.000Z')
    expect(sent.has('action')).toBe(false)
    expect(router.state.location.search).toBe('?account=lan.anh%40example.com&category=LOGIN&from=2026-10-14&to=2026-10-14')
  })

  it('đọc bộ lọc từ đường dẫn, và chọn nhóm thì chỉ gợi ý loại thao tác của nhóm đó', async () => {
    const searches = captureSearches()
    const { user } = renderPage('?category=SETTINGS&action=UI_PREFERENCES_CHANGED&objectType=UI_PREFERENCES')

    await waitFor(() => expect(searches).toHaveLength(1))
    expect(searches[0].get('action')).toBe('UI_PREFERENCES_CHANGED')
    expect(searches[0].get('objectType')).toBe('UI_PREFERENCES')
    const action = screen.getByLabelText('Loại thao tác')
    expect(action).toHaveValue('UI_PREFERENCES_CHANGED')
    expect(within(action).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Tất cả loại thao tác',
      'Cập nhật hồ sơ',
      'Đổi ảnh đại diện',
      'Gỡ ảnh đại diện',
      'Đổi tùy chọn giao diện',
    ])

    // Đổi sang nhóm khác: loại thao tác cũ không thuộc nhóm mới nên bị bỏ.
    await user.selectOptions(screen.getByLabelText('Nhóm thao tác'), 'LOGIN')
    expect(action).toHaveValue('')
    expect(within(action).getByRole('option', { name: 'Đăng nhập thất bại' })).toBeInTheDocument()
  })

  it('ngày bắt đầu sau ngày kết thúc thì báo lỗi và không tra cứu', async () => {
    const searches = captureSearches()
    const { user } = renderPage()
    await waitFor(() => expect(searches).toHaveLength(1))

    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-15')
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-01')
    await user.click(screen.getByRole('button', { name: 'Tra cứu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ngày bắt đầu cần trước hoặc trùng ngày kết thúc.')
    expect(screen.getByLabelText('Từ ngày')).toHaveAttribute('aria-invalid', 'true')
    expect(searches).toHaveLength(1)
  })

  it('mở chi tiết thấy giá trị trước và sau, mã yêu cầu, và tên trường nội dung đã bị loại', async () => {
    const change: AuditLogEntry = {
      ...sampleAuditLogs.items[0],
      id: 'c0ffee00-0000-4000-8000-000000000001',
      action: 'UI_PREFERENCES_CHANGED',
      category: 'SETTINGS',
      objectType: 'UI_PREFERENCES',
      details: {
        changes: [{ field: 'themeMode', before: 'SYSTEM', after: 'DARK' }],
        removedFields: ['note_text'],
        source: 'settings',
      },
      requestId: 'req-abc',
    }
    captureSearches(() => ({ items: [change], nextCursor: null }))
    const { user } = renderPage()

    const toggle = await screen.findByRole('button', { name: 'Xem chi tiết: Đổi tùy chọn giao diện' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)

    const details = screen.getByTestId('audit-details')
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(toggle).toHaveAttribute('aria-controls', details.id)
    expect(within(details).getByTestId('audit-changes')).toHaveTextContent('themeMode: SYSTEM → DARK')
    expect(details).toHaveTextContent('req-abc')
    expect(details).toHaveTextContent('source')
    expect(within(details).getByTestId('audit-removed')).toHaveTextContent(
      'Trường nội dung đã bị loại khỏi nhật ký: note_text',
    )
  })

  it('tải thêm các dòng cũ hơn bằng con trỏ của trang trước', async () => {
    const searches = captureSearches((params) =>
      params.get('before')
        ? { items: sampleAuditLogs.items.slice(2), nextCursor: null }
        : { items: sampleAuditLogs.items.slice(0, 2), nextCursor: sampleAuditLogs.items[1].id },
    )
    const { user } = renderPage()

    await waitFor(() => expect(rows()).toHaveLength(2))
    expect(screen.getByTestId('audit-count')).toHaveTextContent('2 dòng mới nhất, còn dòng cũ hơn')
    await user.click(screen.getByRole('button', { name: 'Xem các dòng cũ hơn' }))

    await waitFor(() => expect(rows()).toHaveLength(4))
    expect(searches[1].get('before')).toBe(sampleAuditLogs.items[1].id)
    expect(screen.queryByRole('button', { name: 'Xem các dòng cũ hơn' })).not.toBeInTheDocument()
  })

  it('không có dòng nào khớp thì nói rõ và cho xóa bộ lọc', async () => {
    const searches = captureSearches((params) => (params.get('account') ? { items: [], nextCursor: null } : sampleAuditLogs))
    const { user, router } = renderPage('?account=khong-co@example.com')

    expect(await screen.findByText('Không có dòng nào khớp bộ lọc')).toBeInTheDocument()
    await user.click(screen.getAllByRole('button', { name: 'Xóa bộ lọc' })[0])

    await waitFor(() => expect(rows()).toHaveLength(4))
    expect(router.state.location.search).toBe('')
    expect(searches.at(-1)?.has('account')).toBe(false)
  })

  it('lỗi máy chủ thì báo và cho thử lại', async () => {
    let fail = true
    captureSearches(() => (fail ? apiError(500, 'INTERNAL_ERROR') : sampleAuditLogs))
    const { user } = renderPage()

    expect(await screen.findByText('Không tải được nhật ký.')).toBeInTheDocument()
    fail = false
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(rows()).toHaveLength(4))
  })
})
