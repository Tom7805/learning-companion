const UNITS = ['B', 'KB', 'MB', 'GB'] as const

/** Dung lượng tệp dễ đọc theo ngôn ngữ giao diện: 1,2 KB, 3,4 MB... */
export function formatBytes(bytes: number, language: string) {
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024
    unit += 1
  }
  const digits = unit === 0 || value >= 100 ? 0 : 1
  return `${new Intl.NumberFormat(language, { maximumFractionDigits: digits }).format(value)} ${UNITS[unit]}`
}
