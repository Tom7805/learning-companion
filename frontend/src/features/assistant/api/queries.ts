import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { Citation } from '../types'
import { assistantApi } from './assistantApi'

/** Một hội thoại đã lưu; {@code id} trống thì không hỏi máy chủ (hội thoại mới chưa gửi câu nào). */
export function useConversation(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.conversation(id ?? ''),
    queryFn: () => assistantApi.conversation(id!),
    enabled: Boolean(id),
    staleTime: 30_000,
    // 404: không tồn tại hoặc của người khác; hỏi lại cũng vậy.
    retry: false,
  })
}

/**
 * Đoạn gốc của trích dẫn cho khung xem nhanh; hỏi lại khi mở để biết tài liệu còn không. Trong lúc chờ dùng dữ liệu đã
 * có trong câu trả lời.
 */
export function useCitation(citation: Citation, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.citation(citation.id),
    queryFn: () => assistantApi.citation(citation.id),
    enabled,
    staleTime: 30_000,
    retry: false,
    placeholderData: citation,
  })
}

/** Hội thoại của không gian học (hoặc hội thoại riêng khi không có {@code workspaceId}). */
export function useConversations(workspaceId?: string) {
  return useQuery({
    queryKey: queryKeys.conversations(workspaceId ?? null),
    queryFn: () => assistantApi.conversations(workspaceId),
    staleTime: 15_000,
  })
}
