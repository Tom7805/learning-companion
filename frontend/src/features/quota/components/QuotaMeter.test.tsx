import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { sampleQuota } from '@/test/mocks/handlers'
import { renderWithProviders } from '@/test/renderWithProviders'
import { setDisplayTimeZone } from '@/shared/lib/timezone'
import { QuotaMeter } from './QuotaMeter'

describe('QuotaMeter', () => {
  beforeEach(() => setDisplayTimeZone('Asia/Ho_Chi_Minh'))
  afterEach(() => setDisplayTimeZone(undefined))

  it('thanh bên: số lượt còn lại, giờ hồi lại, mở trang chi tiết', async () => {
    renderWithProviders(<QuotaMeter ai={sampleQuota.ai} variant="sidebar" />)
    const meter = await screen.findByRole('link', { name: /Lượt AI hôm nay: còn 9 trên 50\. Hồi lại lúc 00:00/ })
    expect(meter).toHaveAttribute('href', '/usage')
    expect(meter).toHaveAttribute('data-level', 'low')
    expect(meter).toHaveTextContent('9/50')
  })

  it('thu gọn: chỉ còn vòng tròn kèm số; hết lượt thì báo đã hết', async () => {
    const { unmount } = renderWithProviders(<QuotaMeter ai={sampleQuota.ai} variant="compact" />)
    expect(await screen.findByTestId('quota-meter')).toHaveTextContent('9')
    unmount()
    renderWithProviders(
      <QuotaMeter ai={{ ...sampleQuota.ai, remainingUnits: 0, usedUnits: 50 }} variant="sidebar" />,
    )
    const meter = await screen.findByTestId('quota-meter')
    expect(meter).toHaveAttribute('data-level', 'empty')
    expect(meter).toHaveTextContent('Đã hết lượt · Hồi lại lúc 00:00')
  })
})
