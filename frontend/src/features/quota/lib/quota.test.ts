import { describe, expect, it } from 'vitest'
import { formatResetTime, quotaErrorInfo, quotaLevel, remainingPercent } from './quota'

describe('quotaLevel', () => {
  it('hết lượt, sắp hết (dưới một phần năm) và còn nhiều', () => {
    expect(quotaLevel({ remainingUnits: 0, dailyUnits: 50 })).toBe('empty')
    expect(quotaLevel({ remainingUnits: 10, dailyUnits: 50 })).toBe('low')
    expect(quotaLevel({ remainingUnits: 11, dailyUnits: 50 })).toBe('plenty')
  })

  it('phần trăm còn lại luôn nằm trong 0 đến 100', () => {
    expect(remainingPercent({ remainingUnits: 9, dailyUnits: 10 })).toBe(90)
    expect(remainingPercent({ remainingUnits: 0, dailyUnits: 0 })).toBe(0)
    expect(remainingPercent({ remainingUnits: 60, dailyUnits: 50 })).toBe(100)
  })
})

describe('formatResetTime', () => {
  const now = new Date('2026-11-03T10:00:00Z')

  it('nửa đêm giờ Việt Nam hiện 00:00, không kèm ngày khi là đêm nay', () => {
    expect(formatResetTime('2026-11-03T17:00:00Z', 'vi-VN', 'Asia/Ho_Chi_Minh', now)).toEqual({
      time: '00:00',
      date: undefined,
    })
  })

  it('xa hơn ngày mai thì kèm ngày', () => {
    expect(formatResetTime('2026-11-07T17:00:00Z', 'vi-VN', 'Asia/Ho_Chi_Minh', now).date).toBe('8/11')
  })
})

describe('quotaErrorInfo', () => {
  const base = { message: '', status: 429, fieldErrors: [] }

  it('hết lượt kèm giờ hồi lại', () => {
    expect(
      quotaErrorInfo({
        ...base,
        code: 'AI_QUOTA_EXHAUSTED',
        details: { resetsAt: '2026-11-03T17:00:00Z', remainingUnits: 2, requiredUnits: 5 },
      }),
    ).toEqual({ kind: 'exhausted', resetsAt: '2026-11-03T17:00:00Z', remainingUnits: 2, requiredUnits: 5 })
  })

  it('tác vụ lỗi đã được hoàn lượt', () => {
    expect(
      quotaErrorInfo({
        ...base,
        status: 503,
        code: 'AI_TEMPORARILY_UNAVAILABLE',
        details: { quotaRefunded: true, refundedUnits: 5 },
      }),
    ).toEqual({ kind: 'refunded', units: 5 })
  })

  it('hết dung lượng, và lỗi khác thì không liên quan hạn mức', () => {
    expect(quotaErrorInfo({ ...base, code: 'STORAGE_QUOTA_EXCEEDED', details: { usedBytes: 10, limitBytes: 12 } }))
      .toEqual({ kind: 'storage', usedBytes: 10, limitBytes: 12 })
    expect(quotaErrorInfo({ ...base, code: 'AI_TEMPORARILY_UNAVAILABLE' })).toBeUndefined()
    expect(quotaErrorInfo(undefined)).toBeUndefined()
  })
})
