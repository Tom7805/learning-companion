import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { SessionStatus } from '../types'
import { authApi } from './authApi'

export function useSession() {
  return useQuery({ queryKey: queryKeys.session, queryFn: authApi.session, staleTime: 60_000 })
}

export function useRegister() {
  return useMutation({ mutationFn: authApi.register })
}

export function useResendVerification() {
  return useMutation({ mutationFn: authApi.resendVerification })
}

export function useVerifyEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: authApi.verifyEmail,
    onSuccess: (account) => {
      // Xác thực xong là đã đăng nhập: cập nhật phiên ngay, không cần gọi lại máy chủ.
      queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: true, account })
    },
  })
}

export function useLogin() {
  return useMutation({ mutationFn: authApi.login })
}

/**
 * Xóa phiên khỏi bộ nhớ đệm rồi gọi `afterLogout` (thường là chuyển trang) ngay trong cùng nhịp.
 * React Query cập nhật giao diện theo lô nên trang đích được dựng với phiên đã xóa, tránh việc
 * trang được bảo vệ hoặc trang đăng nhập tự chuyển hướng sang nơi khác trước.
 */
export function useLogout(afterLogout?: () => void) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: false })
      afterLogout?.()
    },
  })
}

export function useDeviceSessions() {
  return useQuery({ queryKey: queryKeys.deviceSessions, queryFn: authApi.sessions, staleTime: 10_000 })
}

export function useRevokeSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: authApi.revokeSession,
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.deviceSessions }),
  })
}

export function useRevokeOtherSessions() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: authApi.revokeOtherSessions,
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.deviceSessions }),
  })
}
