export const STRONG_PASSWORD = 'mùa thu hà nội 2026'
export const BREACHED_PASSWORD = 'Password1234'
export const DISPLAY_NAME = 'Lan Anh'

export function uniqueEmail(tag = 'hoc') {
  return `${tag}.${Date.now()}.${Math.random().toString(36).slice(2, 7)}@example.com`
}
