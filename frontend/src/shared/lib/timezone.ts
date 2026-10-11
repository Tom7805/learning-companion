import { useSyncExternalStore } from 'react'

/**
 * Múi giờ dùng để hiển thị mọi mốc thời gian (QTN-09): múi giờ trong hồ sơ người học,
 * chưa chọn thì dùng múi giờ của thiết bị. Khung ứng dụng cập nhật giá trị này khi hồ sơ tải xong.
 */
let displayTimeZone: string | undefined
const listeners = new Set<() => void>()

export function setDisplayTimeZone(timeZone: string | undefined) {
  if (timeZone === displayTimeZone) return
  displayTimeZone = timeZone
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Trả về múi giờ hiển thị; undefined nghĩa là theo thiết bị. */
export function useDisplayTimeZone() {
  return useSyncExternalStore(subscribe, () => displayTimeZone)
}

/** Tên cũ của một số múi giờ mà trình duyệt còn trả về, đổi sang tên hiện hành. */
const ALIASES: Record<string, string> = {
  'Asia/Saigon': 'Asia/Ho_Chi_Minh',
  'Asia/Calcutta': 'Asia/Kolkata',
  'Asia/Katmandu': 'Asia/Kathmandu',
  'Asia/Rangoon': 'Asia/Yangon',
  'Europe/Kiev': 'Europe/Kyiv',
  'America/Buenos_Aires': 'America/Argentina/Buenos_Aires',
}

export function normalizeTimeZone(timeZone: string) {
  return ALIASES[timeZone] ?? timeZone
}

export function deviceTimeZone() {
  return normalizeTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone)
}

/** Độ lệch so với giờ chuẩn tại một thời điểm, dạng "GMT+07:00". */
export function timeZoneOffset(timeZone: string, at: Date = new Date()) {
  const part = new Intl.DateTimeFormat('en-US', { timeZone, timeZoneName: 'longOffset' })
    .formatToParts(at)
    .find((item) => item.type === 'timeZoneName')
  return part?.value === 'GMT' ? 'GMT+00:00' : (part?.value ?? 'GMT+00:00')
}

function offsetMinutes(offset: string) {
  const match = /GMT([+-])(\d{2}):(\d{2})/.exec(offset)
  if (!match) return 0
  const minutes = Number(match[2]) * 60 + Number(match[3])
  return match[1] === '-' ? -minutes : minutes
}

const CITY_NAMES: Record<string, string> = {
  'Asia/Ho_Chi_Minh': 'Hồ Chí Minh (Việt Nam)',
}

export function timeZoneCity(timeZone: string) {
  return CITY_NAMES[timeZone] ?? timeZone.split('/').pop()!.replaceAll('_', ' ')
}

export function timeZoneLabel(timeZone: string, at: Date = new Date()) {
  return `(${timeZoneOffset(timeZone, at)}) ${timeZoneCity(timeZone)}`
}

export interface TimeZoneOption {
  value: string
  label: string
}

/** Danh sách múi giờ theo vùng, sắp theo độ lệch rồi theo tên thành phố. */
export function timeZoneOptions(include: (string | null | undefined)[] = [], at: Date = new Date()): TimeZoneOption[] {
  const all =
    typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['Asia/Ho_Chi_Minh', 'UTC']
  const zones = new Set(all.map(normalizeTimeZone))
  include.forEach((zone) => zone && zones.add(normalizeTimeZone(zone)))
  return [...zones]
    .map((zone) => ({ zone, offset: timeZoneOffset(zone, at) }))
    .sort((a, b) => offsetMinutes(a.offset) - offsetMinutes(b.offset) || timeZoneCity(a.zone).localeCompare(timeZoneCity(b.zone)))
    .map(({ zone, offset }) => ({ value: zone, label: `(${offset}) ${timeZoneCity(zone)}` }))
}
