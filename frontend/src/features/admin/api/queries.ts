import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { adminApi, type AuditLogSearch } from './adminApi'

/**
 * Hỏi máy chủ xem người đang đăng nhập có phải quản trị viên không. Không thử lại khi bị từ chối: máy chủ đã ghi lần
 * từ chối, hỏi lại chỉ ghi thêm dòng trùng.
 */
export function useAdminIdentity() {
  return useQuery({
    queryKey: queryKeys.adminMe,
    queryFn: adminApi.me,
    retry: false,
    staleTime: 60_000,
  })
}

/** Kết quả tra cứu nhật ký, mới nhất trước, tải thêm trang cũ hơn khi cần. Mỗi lần tra cứu máy chủ đều ghi lại. */
export function useAuditLogs(search: AuditLogSearch) {
  return useInfiniteQuery({
    queryKey: queryKeys.auditLogs(search),
    queryFn: ({ pageParam }) => adminApi.auditLogs(search, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    retry: false,
    staleTime: 30_000,
  })
}
