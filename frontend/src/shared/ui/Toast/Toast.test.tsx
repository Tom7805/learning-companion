import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { toast, Toaster } from '.'

const messages = () => screen.queryAllByTestId('toast').map((item) => item.textContent)

describe('Toast', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('hiện trong vùng đọc to, tự ẩn sau năm giây', () => {
    render(<Toaster />)
    const region = screen.getByTestId('toaster')
    expect(region).toHaveAttribute('aria-live', 'polite')
    expect(region).toHaveAccessibleName('Thông báo')

    act(() => toast.success('Đã kết nối lại.'))
    expect(within(region).getByText('Đã kết nối lại.')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(4_999))
    expect(messages()).toEqual(['Đã kết nối lại.'])
    act(() => vi.advanceTimersByTime(1))
    expect(messages()).toEqual([])
  })

  it('đóng tay được; cùng lời nhắn không chồng, nhiều nhất ba thông báo', () => {
    render(<Toaster />)

    act(() => {
      toast.info('Một')
      toast.info('Một')
      toast.error('Hai')
      toast.success('Ba')
      toast.info('Bốn')
    })
    expect(messages()).toEqual(['Hai', 'Ba', 'Bốn'])

    fireEvent.click(within(screen.getAllByTestId('toast')[0]).getByRole('button', { name: 'Đóng' }))
    expect(messages()).toEqual(['Ba', 'Bốn'])
  })
})
