import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { AiDisclosure, Citation } from '../types'
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

/**
 * Lịch sử hội thoại của không gian học (hoặc hội thoại riêng khi không có {@code workspaceId}); {@code query} tìm theo
 * tên và nội dung (NCL-07-CN-003). Đang gõ từ khóa thì giữ kết quả cũ cho tới khi có kết quả mới.
 */
export function useConversations(workspaceId?: string, query = '') {
  return useQuery({
    queryKey: queryKeys.conversations(workspaceId ?? null, query.trim()),
    queryFn: () => assistantApi.conversations(workspaceId, query),
    staleTime: 15_000,
    placeholderData: keepPreviousData,
  })
}

/** Đổi tên, ghim, chuyển vào không gian học hay xóa một hội thoại; xong thì làm mới mọi danh sách và hội thoại đó. */
export function useConversationActions() {
  const client = useQueryClient()
  const refresh = (id: string) => {
    void client.invalidateQueries({ queryKey: queryKeys.conversationLists })
    void client.invalidateQueries({ queryKey: queryKeys.conversation(id) })
  }
  const update = useMutation({
    mutationFn: ({ id, ...change }: { id: string; title?: string; pinned?: boolean }) =>
      assistantApi.updateConversation(id, change),
    onSuccess: (_, { id }) => refresh(id),
  })
  const move = useMutation({
    mutationFn: ({ id, workspaceId }: { id: string; workspaceId: string | null }) =>
      assistantApi.moveConversation(id, workspaceId),
    onSuccess: (_, { id }) => refresh(id),
  })
  const remove = useMutation({
    mutationFn: (id: string) => assistantApi.deleteConversation(id),
    onSuccess: (_, id) => {
      void client.invalidateQueries({ queryKey: queryKeys.conversationLists })
      client.removeQueries({ queryKey: queryKeys.conversation(id) })
    },
  })
  return { update, move, remove }
}

/** Thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo đã được đọc chưa (NCL-04-CN-005 TC-01). */
export function useAiDisclosure() {
  return useQuery({
    queryKey: queryKeys.aiDisclosure,
    queryFn: () => assistantApi.disclosure(),
    staleTime: Infinity,
    retry: false,
  })
}

/** Ghi nhận đã đọc; giao diện đóng thông báo ngay, không chờ máy chủ. */
export function useAcknowledgeDisclosure() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => assistantApi.acknowledgeDisclosure(),
    onMutate: () => {
      const previous = client.getQueryData<AiDisclosure>(queryKeys.aiDisclosure)
      client.setQueryData<AiDisclosure>(queryKeys.aiDisclosure, { version: previous?.version ?? 1, acknowledged: true })
    },
    onSuccess: (data) => client.setQueryData(queryKeys.aiDisclosure, data),
  })
}
