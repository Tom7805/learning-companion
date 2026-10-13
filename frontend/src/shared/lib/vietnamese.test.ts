import { describe, expect, it } from 'vitest'
import { foldVietnamese, matchesSearch } from './vietnamese'

describe('foldVietnamese', () => {
  it('bỏ dấu, đổi đ thành d và viết thường', () => {
    expect(foldVietnamese('  Định luật Ôm – Điện trở ')).toBe('dinh luat om – dien tro')
  })
})

describe('matchesSearch', () => {
  it('khớp khi mọi từ có mặt, không phân biệt dấu và thứ tự', () => {
    expect(matchesSearch('Ghi chú: Định luật Ohm', 'ohm dinh')).toBe(true)
    expect(matchesSearch('Ghi chú: Định luật Ohm', 'ĐỊNH LUẬT')).toBe(true)
    expect(matchesSearch('Ghi chú: Định luật Ohm', 'newton')).toBe(false)
  })

  it('câu tìm rỗng khớp mọi thứ', () => {
    expect(matchesSearch('Thư viện', '   ')).toBe(true)
  })
})
