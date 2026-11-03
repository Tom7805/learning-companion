import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { AiFeature } from '../types'
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

/** Toàn cảnh lớp kết nối trí tuệ nhân tạo: định tuyến, trạng thái mạch, nhà cung cấp, cảnh báo. Tự làm mới mỗi 15 giây. */
export function useAiGateway() {
  return useQuery({
    queryKey: queryKeys.aiGateway,
    queryFn: adminApi.aiGateway,
    retry: false,
    refetchInterval: 15_000,
  })
}

export function useAiUsage(days: number) {
  return useQuery({
    queryKey: queryKeys.aiUsage(days),
    queryFn: () => adminApi.aiUsage(days),
    retry: false,
    staleTime: 10_000,
  })
}

/** Thay đổi trên lớp kết nối: xong thì tải lại toàn cảnh và mức sử dụng. */
export function useAiGatewayMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () => {
      void client.invalidateQueries({ queryKey: queryKeys.aiGateway })
      void client.invalidateQueries({ queryKey: ['admin', 'ai', 'usage'] })
    },
  })
}

/** Chức năng trí tuệ nhân tạo cùng phiên bản đang phát hành của từng chức năng và các bộ nguồn mẫu. */
export function useAiConfigs() {
  return useQuery({ queryKey: queryKeys.aiConfigs, queryFn: adminApi.aiConfigs, retry: false })
}

export function useAiConfigVersions(feature: AiFeature) {
  return useQuery({
    queryKey: queryKeys.aiConfigVersions(feature),
    queryFn: () => adminApi.aiConfigVersions(feature),
    retry: false,
  })
}

export function useAiConfigDiff(feature: AiFeature, from: number | null, to: number | null) {
  return useQuery({
    queryKey: queryKeys.aiConfigDiff(feature, from ?? 0, to ?? 0),
    queryFn: () => adminApi.aiConfigDiff(feature, from as number, to as number),
    enabled: from != null && to != null && from !== to,
    retry: false,
    staleTime: Infinity,
  })
}

/** Lưu, phát hành hay quay lại cấu hình: xong thì tải lại danh sách chức năng và lịch sử phiên bản. */
export function useAiConfigMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn,
    onSettled: () => void client.invalidateQueries({ queryKey: queryKeys.aiConfigs }),
  })
}
