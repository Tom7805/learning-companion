import { QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { onAccountChanged, onSessionLost } from '@/shared/api/httpClient'
import { createQueryClient } from '@/shared/api/queryClient'
import { queryKeys } from '@/shared/api/queryKeys'

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(createQueryClient)

  // Bất kỳ yêu cầu nào báo mất phiên thì cập nhật trạng thái đăng nhập, trang được bảo vệ tự chuyển hướng.
  useEffect(
    () =>
      onSessionLost((reason) => {
        const specific = reason === 'UNAUTHENTICATED' ? undefined : reason
        const current = client.getQueryData<{ authenticated: boolean; reason?: string }>(queryKeys.session)
        // Trang gửi nhiều yêu cầu song song: yêu cầu nào về trước có thể chỉ báo "chưa đăng nhập". Lý do cụ thể (phiên
        // bị thu hồi, hết hạn) về sau vẫn được ghi lại để trang đăng nhập giải thích đúng.
        if (current?.authenticated || (current && !current.reason && specific)) {
          client.setQueryData(queryKeys.session, { authenticated: false, reason: specific })
        }
      }),
    [client],
  )

  useEffect(() => onAccountChanged(() => void client.invalidateQueries({ queryKey: queryKeys.session })), [client])

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
