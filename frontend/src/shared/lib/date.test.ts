import { describe, expect, it } from 'vitest'
import { formatRelative } from './date'

const NOW = Date.parse('2026-10-10T03:00:00Z')

describe('formatRelative', () => {
  it.each([
    ['2026-10-10T02:59:30Z', 'bây giờ'],
    ['2026-10-10T02:55:00Z', '5 phút trước'],
    ['2026-10-10T01:00:00Z', '2 giờ trước'],
    ['2026-10-08T03:00:00Z', 'Hôm kia'],
  ])('%s → %s', (iso, expected) => {
    expect(formatRelative(iso, 'vi', NOW)).toBe(expected)
  })

  it('thời điểm trong tương lai do lệch đồng hồ coi như vừa xong', () => {
    expect(formatRelative('2026-10-10T03:15:00Z', 'vi', NOW)).toBe(formatRelative('2026-10-10T03:00:00Z', 'vi', NOW))
  })
})
