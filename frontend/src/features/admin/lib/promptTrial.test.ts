import { describe, expect, it } from 'vitest'
import { splitByFindings } from './promptTrial'

describe('splitByFindings', () => {
  it('tách đúng đoạn chứa dữ liệu cá nhân theo vị trí máy chủ trả về', () => {
    const text = 'Viết cho lan@truong.vn hoặc gọi 0912345678.'
    const start = text.indexOf('lan@')
    const phone = text.indexOf('0912')
    expect(
      splitByFindings(text, [
        { kind: 'PHONE', start: phone, end: phone + 10 },
        { kind: 'EMAIL', start, end: start + 'lan@truong.vn'.length },
      ]),
    ).toEqual([
      { text: 'Viết cho ' },
      { text: 'lan@truong.vn', kind: 'EMAIL' },
      { text: ' hoặc gọi ' },
      { text: '0912345678', kind: 'PHONE' },
      { text: '.' },
    ])
  })

  it('vị trí vượt quá độ dài chuỗi hay chồng nhau không làm mất chữ', () => {
    expect(splitByFindings('abc', [{ kind: 'EMAIL', start: 1, end: 99 }]).map((part) => part.text).join('')).toBe(
      'abc',
    )
    expect(splitByFindings('abcdef', [
      { kind: 'EMAIL', start: 0, end: 4 },
      { kind: 'PHONE', start: 2, end: 6 },
    ]).map((part) => part.text).join('')).toBe('abcdef')
  })
})
