import { useLayoutEffect, type ReactNode } from 'react'
import { useUiPreferences } from '../store/uiStore'
import { applyAppearance, useEffectiveTheme } from './appearance'

/**
 * Áp chế độ sáng tối và cỡ chữ lên toàn trang (NCL-02-CN-001). Đoạn mã nhỏ trong index.html đã áp sẵn trước khi
 * ứng dụng tải để không nháy màu; ở đây giữ cho giao diện theo kịp khi người học đổi tùy chọn hoặc hệ điều hành
 * chuyển sáng tối.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { fontSize } = useUiPreferences()
  const theme = useEffectiveTheme()
  useLayoutEffect(() => applyAppearance(theme, fontSize), [theme, fontSize])
  return children
}
