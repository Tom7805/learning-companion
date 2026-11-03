import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '@/test/renderWithProviders'
import { FlaggedSourcesNote, SafetyNotice } from './SafetyNotice'

describe('SafetyNotice', () => {
  it('TC-02: câu hỏi bị chặn hiện lý do theo nhóm và gợi ý cách hỏi khác', () => {
    renderWithProviders(
      <SafetyNotice outcome={{ status: 'BLOCKED', stage: 'INPUT', category: 'HARMFUL_INSTRUCTIONS' }} />,
    )
    const notice = screen.getByRole('alert')
    expect(notice).toHaveTextContent('Trợ lý không trả lời câu hỏi này')
    expect(notice).toHaveTextContent('thuộc nhóm hướng dẫn gây hại')
    expect(notice).toHaveTextContent('không tốn lượt')
    expect(notice).toHaveTextContent('Bạn có thể hỏi theo cách khác:')
    expect(notice).toHaveTextContent('Vì sao thuốc nổ được quản lý chặt chẽ?')
  })

  it('TC-04: nội dung sinh ra vi phạm được thay bằng thông báo chặn', () => {
    renderWithProviders(<SafetyNotice outcome={{ status: 'BLOCKED', stage: 'OUTPUT', category: 'HATE' }} />)
    const notice = screen.getByRole('alert')
    expect(notice).toHaveAttribute('data-stage', 'OUTPUT')
    expect(notice).toHaveTextContent('Câu trả lời vừa rồi đã được ẩn')
    expect(notice).toHaveTextContent('thuộc nhóm thù ghét')
  })

  it('TC-03: dấu hiệu tự gây hại hiện lời ân cần và nguồn trợ giúp gọi được ngay', () => {
    renderWithProviders(
      <SafetyNotice
        outcome={{
          status: 'SUPPORT',
          stage: 'INPUT',
          category: 'SELF_HARM',
          support: {
            language: 'vi',
            message: 'Mình rất tiếc khi bạn đang phải trải qua cảm giác nặng nề như vậy.',
            resources: [
              { name: 'Cấp cứu', contact: '115', note: 'Gọi ngay khi gặp nguy hiểm.' },
              { name: 'Người bạn tin tưởng', contact: null, note: 'Người thân, thầy cô.' },
            ],
          },
        }}
      />,
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bạn không một mình' })).toBeInTheDocument()
    expect(screen.getByText(/cảm giác nặng nề/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Gọi 115' })).toHaveAttribute('href', 'tel:115')
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByText('Người bạn tin tưởng')).toBeInTheDocument()
  })

  it('TC-01: ghi chú đoạn nguồn bị gắn cờ, không hiện gì khi không có đoạn nào', () => {
    const { rerender } = renderWithProviders(<FlaggedSourcesNote sources={[1, 3]} />)
    expect(screen.getByTestId('safety-flagged-sources')).toHaveTextContent('Đoạn nguồn [1], [3] có câu nghi chèn')
    rerender(<FlaggedSourcesNote sources={[]} />)
    expect(screen.queryByTestId('safety-flagged-sources')).not.toBeInTheDocument()
  })
})
