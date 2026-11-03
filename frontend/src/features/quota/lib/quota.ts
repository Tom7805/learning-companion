import type { ApiError } from '@/shared/api/errors'
import type { QuotaStatus } from '../types'

export type QuotaLevel = 'plenty' | 'low' | 'empty'

/** Mức của đồng hồ lượt: hết lượt, sắp hết (còn dưới một phần năm) hoặc còn nhiều. */
export function quotaLevel(ai: Pick<QuotaStatus['ai'], 'remainingUnits' | 'dailyUnits'>): QuotaLevel {
  if (ai.remainingUnits <= 0) return 'empty'
  if (ai.remainingUnits * 5 <= ai.dailyUnits) return 'low'
  return 'plenty'
}

/** Phần trăm lượt còn lại, từ 0 đến 100, để vẽ thanh và vòng tròn. */
export function remainingPercent(ai: Pick<QuotaStatus['ai'], 'remainingUnits' | 'dailyUnits'>) {
  if (ai.dailyUnits <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((ai.remainingUnits / ai.dailyUnits) * 100)))
}

/** Giờ hồi lượt (thường là 00:00) theo múi giờ hiển thị; khác ngày hôm nay thì kèm ngày. */
export function formatResetTime(iso: string, language: string, timeZone?: string, now: Date = new Date()) {
  const at = new Date(iso)
  const day = (date: Date) => new Intl.DateTimeFormat('en-CA', { timeZone, dateStyle: 'short' }).format(date)
  const tomorrow = new Date(now.getTime() + 24 * 3600 * 1000)
  const time = new Intl.DateTimeFormat(language, { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .format(at)
  if (day(at) === day(now) || day(at) === day(tomorrow)) {
    return { time, date: undefined }
  }
  return { time, date: new Intl.DateTimeFormat(language, { timeZone, day: 'numeric', month: 'numeric' }).format(at) }
}

/**
 * Hạn mức trong một lỗi từ máy chủ, để chức năng tốn lượt báo đúng cho người học (NCL-04-CN-003):
 * hết lượt thì kèm giờ hồi lại; tác vụ lỗi thì cho biết số lượt đã được hoàn lại.
 */
export type QuotaErrorInfo =
  | { kind: 'exhausted'; resetsAt?: string; remainingUnits?: number; requiredUnits?: number }
  | { kind: 'refunded'; units: number }
  | { kind: 'storage'; usedBytes?: number; limitBytes?: number }

export function quotaErrorInfo(error: ApiError | null | undefined): QuotaErrorInfo | undefined {
  if (!error) return undefined
  const details = error.details ?? {}
  const number = (key: string) => (typeof details[key] === 'number' ? (details[key] as number) : undefined)
  if (error.code === 'AI_QUOTA_EXHAUSTED') {
    return {
      kind: 'exhausted',
      resetsAt: typeof details.resetsAt === 'string' ? details.resetsAt : undefined,
      remainingUnits: number('remainingUnits'),
      requiredUnits: number('requiredUnits'),
    }
  }
  if (error.code === 'STORAGE_QUOTA_EXCEEDED') {
    return { kind: 'storage', usedBytes: number('usedBytes'), limitBytes: number('limitBytes') }
  }
  if (details.quotaRefunded === true) {
    return { kind: 'refunded', units: number('refundedUnits') ?? 0 }
  }
  return undefined
}
