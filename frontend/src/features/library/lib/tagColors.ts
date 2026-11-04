import type { TagColor } from '../types'

/** Màu nền của nhãn lấy từ bảng màu giao diện, nên đủ tương phản ở cả chế độ sáng và tối. */
export const tagSwatch: Record<TagColor, string> = {
  SUN: 'bg-sun text-ink',
  LILAC: 'bg-lilac text-ink',
  SKY: 'bg-sky text-ink',
  MINT: 'bg-mint text-[#1d1d1f]',
  CORAL: 'bg-accent text-on-accent',
  INK: 'bg-ink text-on-ink',
}
