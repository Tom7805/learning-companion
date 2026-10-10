import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { estimatePasswordStrength, passwordLength } from '../lib/passwordStrength'
import { PasswordStrengthMeter } from './PasswordStrengthMeter'

describe('estimatePasswordStrength', () => {
  it.each([
    ['', 0],
    ['abc', 0],
    ['aaaaaaaaaaaa', 0],
    ['1234567890', 0],
    ['Abcdefgh12', 2],
    ['song-xanh-mua-thu-2026', 4],
    ['mùa thu hà nội 2026', 4],
  ])('đánh giá %j ở mức %i', (password, level) => {
    expect(estimatePasswordStrength(password)).toBe(level)
  })

  it('không bao giờ quá mức 1 khi chưa đủ 10 ký tự', () => {
    expect(estimatePasswordStrength('Ab1!Ab1!c')).toBeLessThanOrEqual(1)
  })

  it('đếm ký tự theo code point như máy chủ', () => {
    expect(passwordLength('😀😀😀😀😀')).toBe(5)
    expect(passwordLength('mùa thu')).toBe(7)
  })
})

describe('PasswordStrengthMeter', () => {
  it('đánh dấu đạt độ dài khi đủ 10 ký tự', () => {
    render(<PasswordStrengthMeter password="mùa thu hà nội 2026" />)
    expect(screen.getByTestId('strength-label')).toHaveTextContent('Rất mạnh')
    expect(screen.getByText('Ít nhất 10 ký tự').closest('li')).toHaveAttribute('data-state', 'pass')
    expect(screen.getByText('Không phải mật khẩu phổ biến đã bị lộ').closest('li')).toHaveAttribute(
      'data-state',
      'pending',
    )
  })

  it('chưa đạt độ dài khi quá ngắn', () => {
    render(<PasswordStrengthMeter password="abc" />)
    expect(screen.getByText('Ít nhất 10 ký tự').closest('li')).toHaveAttribute('data-state', 'pending')
  })

  it('báo trượt quy tắc mật khẩu đã lộ khi máy chủ từ chối và hạ về Rất yếu', () => {
    render(<PasswordStrengthMeter password="password1234" breached />)
    expect(screen.getByTestId('strength-label')).toHaveTextContent('Rất yếu')
    expect(screen.getByText('Không phải mật khẩu phổ biến đã bị lộ').closest('li')).toHaveAttribute(
      'data-state',
      'fail',
    )
    expect(screen.getByText('(không đạt)')).toBeInTheDocument()
  })
})
