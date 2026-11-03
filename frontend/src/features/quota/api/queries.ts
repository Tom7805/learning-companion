import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { AdminQuota } from '../types'
import { quotaApi } from './quotaApi'

/**
 * Đồng hồ lượt trên khung ứng dụng. Hỏi lại mỗi phút và khi quay lại thẻ, để lượt vừa trừ, vừa hoàn hay vừa hồi lúc
 * nửa đêm hiện ra mà không cần tải lại trang.
 */
export function useQuotaStatus(enabled = true) {
  return useQuery({
    queryKey: queryKeys.quota,
    queryFn: quotaApi.status,
    enabled,
    staleTime: 15_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })
}

export function useAdminQuota() {
  return useQuery({ queryKey: queryKeys.adminQuota, queryFn: quotaApi.adminOverview, retry: false })
}

/** Lưu hạn mức hoặc bảng quy đổi; máy chủ trả lại toàn trang nên ghi thẳng vào bộ nhớ đệm. */
export function useAdminQuotaMutation<TInput>(action: (input: TInput) => Promise<AdminQuota>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: action,
    onSuccess: (data) => {
      client.setQueryData(queryKeys.adminQuota, data)
      void client.invalidateQueries({ queryKey: queryKeys.quota })
    },
  })
}
