import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { AdminQuota } from '@/features/quota/types'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { QuotasPage } from './QuotasPage'

const overview: AdminQuota = {
  policy: { dailyAiUnits: 50, storageLimitBytes: 1073741824, updatedAt: '2026-11-03T08:00:00Z', updatedBy: null },
  costs: [
    { task: 'ASSISTANT_QUESTION', weight: 'LIGHT', units: 1, defaultUnits: 1, updatedAt: '2026-11-03T08:00:00Z' },
    { task: 'QUIZ_GENERATION', weight: 'LONG', units: 5, defaultUnits: 5, updatedAt: '2026-11-03T08:00:00Z' },
  ],
}

describe('QuotasPage', () => {
  it('đổi hạn mức phải xác nhận lại giá trị trước và sau, rồi gửi đúng giá trị (TC-06)', async () => {
    let sent: unknown
    server.use(
      http.get('*/api/v1/admin/quotas', () => HttpResponse.json(overview)),
      http.put('*/api/v1/admin/quotas/policy', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({
          ...overview,
          policy: {
            dailyAiUnits: 20,
            storageLimitBytes: 500 * 1024 * 1024,
            updatedAt: '2026-11-03T09:00:00Z',
            updatedBy: { id: 'a1', displayName: 'Lan Anh' },
          },
        })
      }),
    )
    const { user } = renderWithProviders(<QuotasPage />)
    const policy = await screen.findByTestId('admin-quota-policy')
    expect(policy).toHaveTextContent('Đang dùng giá trị mặc định lúc cài đặt.')
    const daily = within(policy).getByLabelText('Lượt AI mỗi ngày')
    await user.clear(daily)
    await user.type(daily, '20')
    const storage = within(policy).getByLabelText('Dung lượng mỗi người học (MB)')
    await user.clear(storage)
    await user.type(storage, '500')
    await user.click(within(policy).getByRole('button', { name: 'Lưu hạn mức' }))

    const dialog = await screen.findByRole('dialog', { name: 'Xác nhận đổi hạn mức' })
    expect(within(dialog).getByTestId('admin-quota-changes')).toHaveTextContent('Lượt AI mỗi ngày: 50 → 20')
    expect(within(dialog).getByTestId('admin-quota-changes')).toHaveTextContent('(MB): 1024 → 500')
    await user.click(within(dialog).getByRole('button', { name: 'Áp dụng' }))
    await waitFor(() => expect(sent).toEqual({ dailyAiUnits: 20, storageLimitMb: 500 }))
    expect(await screen.findByText('Đã lưu hạn mức mới.')).toBeInTheDocument()
    expect(await screen.findByTestId('admin-quota-last-change')).toHaveTextContent('Sửa lần cuối bởi Lan Anh')
  })

  it('giá trị ngoài khoảng cho phép bị chặn ngay, không gửi lên máy chủ', async () => {
    server.use(http.get('*/api/v1/admin/quotas', () => HttpResponse.json(overview)))
    const { user } = renderWithProviders(<QuotasPage />)
    const policy = await screen.findByTestId('admin-quota-policy')
    const daily = within(policy).getByLabelText('Lượt AI mỗi ngày')
    await user.clear(daily)
    await user.type(daily, '0')
    await user.click(within(policy).getByRole('button', { name: 'Lưu hạn mức' }))
    expect(within(policy).getByText('Nhập số nguyên từ 1 đến 10 000.')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const costs = screen.getByTestId('admin-quota-costs')
    const quiz = within(costs).getByLabelText('Số lượt: Sinh bài kiểm tra')
    await user.clear(quiz)
    await user.type(quiz, '101')
    await user.click(within(costs).getByRole('button', { name: 'Lưu bảng quy đổi' }))
    expect(within(costs).getByText('Từ 1 đến 100.')).toBeInTheDocument()
  })

  it('bảng quy đổi chỉ gửi các loại tác vụ đã đổi', async () => {
    let sent: unknown
    server.use(
      http.get('*/api/v1/admin/quotas', () => HttpResponse.json(overview)),
      http.put('*/api/v1/admin/quotas/costs', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(overview)
      }),
    )
    const { user } = renderWithProviders(<QuotasPage />)
    const costs = await screen.findByTestId('admin-quota-costs')
    const quiz = within(costs).getByLabelText('Số lượt: Sinh bài kiểm tra')
    await user.clear(quiz)
    await user.type(quiz, '8')
    await user.click(within(costs).getByRole('button', { name: 'Lưu bảng quy đổi' }))
    const dialog = await screen.findByRole('dialog', { name: 'Xác nhận đổi bảng quy đổi' })
    expect(dialog).toHaveTextContent('Sinh bài kiểm tra: 5 → 8')
    await user.click(within(dialog).getByRole('button', { name: 'Áp dụng' }))
    await waitFor(() => expect(sent).toEqual({ costs: [{ task: 'QUIZ_GENERATION', units: 8 }] }))
  })
})
