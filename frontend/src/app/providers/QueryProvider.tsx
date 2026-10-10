import { QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { onSessionLost } from '@/shared/api/httpClient'
import { createQueryClient } from '@/shared/api/queryClient'
import { queryKeys } from '@/shared/api/queryKeys'

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(createQueryClient)

  // Bất kỳ yêu cầu nào báo mất phiên thì cập nhật trạng thái đăng nhập, trang được bảo vệ tự chuyển hướng.
  useEffect(
    () =>
      onSessionLost((reason) => {
        if (client.getQueryData<{ authenticated: boolean }>(queryKeys.session)?.authenticated) {
          client.setQueryData(queryKeys.session, {
            authenticated: false,
            reason: reason === 'UNAUTHENTICATED' ? undefined : reason,
          })
        }
      }),
    [client],
  )

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
