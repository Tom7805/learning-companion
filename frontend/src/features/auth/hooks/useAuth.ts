import { useSession } from '../api/queries'

/** Tài khoản đang đăng nhập (nếu có) và trạng thái tải phiên. */
export function useAuth() {
  const session = useSession()
  return {
    account: session.data?.authenticated ? session.data.account : undefined,
    isAuthenticated: Boolean(session.data?.authenticated),
    isLoading: session.isPending,
    isError: session.isError,
    refetch: session.refetch,
  }
}
