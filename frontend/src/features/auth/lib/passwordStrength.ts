export const PASSWORD_MIN_LENGTH = 10
export const PASSWORD_MAX_LENGTH = 128

export type StrengthLevel = 0 | 1 | 2 | 3 | 4

/** Đếm theo ký tự thật (code point) giống máy chủ, nên chữ có dấu hay biểu tượng không bị đếm sai. */
export function passwordLength(password: string) {
  return [...password].length
}

/**
 * Ước lượng độ mạnh để gợi ý khi gõ, ưu tiên độ dài như cụm từ. Máy chủ mới là nơi quyết định
 * (độ dài tối thiểu và danh sách mật khẩu đã lộ), thước đo này chỉ để hướng dẫn.
 */
export function estimatePasswordStrength(password: string): StrengthLevel {
  if (!password) return 0
  const length = passwordLength(password)
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9\s]/, /\s/].filter((pattern) =>
    pattern.test(password),
  ).length
  const uniqueRatio = new Set(password.toLowerCase()).size / length

  let score = 0
  if (length >= PASSWORD_MIN_LENGTH) score = 1
  if (length >= 12) score = 2
  if (length >= 16) score = 3
  if (length >= 20) score = 4
  if (classes >= 3) score += 1
  if (uniqueRatio < 0.4) score -= 2
  if (/^(\d+|[a-z]+|[A-Z]+)$/.test(password) && length < 16) score -= 1
  if (length < PASSWORD_MIN_LENGTH) score = Math.min(score, 1)

  return Math.max(0, Math.min(4, score)) as StrengthLevel
}
