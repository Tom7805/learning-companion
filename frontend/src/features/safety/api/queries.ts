import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { contentSafetyApi } from './contentSafetyApi'

export function useContentSafety() {
  return useQuery({ queryKey: queryKeys.contentSafety, queryFn: contentSafetyApi.overview, retry: false })
}

/** Lưu ngưỡng lọc; máy chủ trả lại toàn trang nên ghi thẳng vào bộ nhớ đệm. */
export function useSafetyLevelsMutation() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: contentSafetyApi.updateLevels,
    onSuccess: (data) => client.setQueryData(queryKeys.contentSafety, data),
  })
}
