import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { sampleQuota } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { setDisplayTimeZone } from '@/shared/lib/timezone'
import { UsagePage } from './UsagePage'

describe('UsagePage', () => {
  beforeEach(() => setDisplayTimeZone('Asia/Ho_Chi_Minh'))
  afterEach(() => setDisplayTimeZone(undefined))

  it('hiện lượt còn lại, giờ hồi lại, dung lượng và phần nào tốn lượt', async () => {
    renderWithProviders(<UsagePage />)
    expect(await screen.findByTestId('quota-ai-remaining')).toHaveTextContent('Còn 9 / 50 lượt')
    expect(screen.getByTestId('quota-ai-used')).toHaveTextContent('41')
    expect(screen.getByTestId('quota-ai-resets')).toHaveTextContent('lúc 00:00')
    expect(screen.getByTestId('quota-storage')).toHaveTextContent('50 MB')
    expect(screen.getByTestId('quota-storage')).toHaveTextContent('trên 1 GB')
    const costs = screen.getByTestId('quota-costs')
    expect(within(costs).getByText('Hỏi đáp trên tài liệu')).toBeInTheDocument()
    expect(costs.querySelector('[data-task="QUIZ_GENERATION"]')).toHaveTextContent('5 lượt')
    expect(screen.getByTestId('quota-free-actions')).toHaveTextContent('Ôn các bộ thẻ ghi nhớ đã có')
    expect(screen.queryByTestId('quota-exhausted')).not.toBeInTheDocument()
  })

  it('hết lượt thì báo giờ hồi lại và gợi ý cách học không tốn lượt (TC-03)', async () => {
    server.use(
      http.get('*/api/v1/quota', () =>
        HttpResponse.json({ ...sampleQuota, ai: { ...sampleQuota.ai, usedUnits: 50, remainingUnits: 0 } }),
      ),
    )
    renderWithProviders(<UsagePage />)
    const notice = await screen.findByTestId('quota-exhausted')
    expect(notice).toHaveTextContent('Bạn đã dùng hết lượt AI hôm nay')
    expect(within(notice).getByTestId('quota-resets-at')).toHaveTextContent('Lượt hồi đủ lúc 00:00')
    expect(within(notice).getByRole('link', { name: 'Mở kho tài liệu và đọc lại bài' })).toHaveAttribute(
      'href',
      '/library',
    )
  })

  it('lỗi tải thì có nút thử lại', async () => {
    server.use(http.get('*/api/v1/quota', () => HttpResponse.json({ code: 'INTERNAL_ERROR' }, { status: 500 })))
    renderWithProviders(<UsagePage />)
    expect(await screen.findByText('Chưa tải được lượt dùng. Hãy thử lại.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  })
})
