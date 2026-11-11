import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { DocumentCard } from '@/features/library/types'
import type { UsageTarget, TopicTree } from '../types'
import { workspacesApi } from './workspacesApi'

/** Hỏi lại máy chủ sau mỗi chừng này khi còn tài liệu đang chờ hoặc đang xử lý. */
const PROCESSING_POLL_MS = 3_000

const inProgress = (document: Pick<DocumentCard, 'status'>) =>
  document.status === 'PENDING' || document.status === 'PROCESSING'

/**
 * Mọi không gian học của người học; {@code enabled} false khi người đăng nhập là quản trị viên. {@code refetchOnMount}
 * false cho chỗ chỉ hiện dữ liệu đã nạp (thanh bên trong ngăn kéo): mở ngăn kéo không gửi yêu cầu nào.
 */
export function useWorkspaces(enabled = true, refetchOnMount = true) {
  return useQuery({
    queryKey: queryKeys.workspaceList,
    queryFn: workspacesApi.list,
    staleTime: 15_000,
    enabled,
    refetchOnMount,
  })
}

export function useWorkspace(id: string) {
  return useQuery({
    queryKey: queryKeys.workspace(id),
    queryFn: () => workspacesApi.get(id),
    staleTime: 15_000,
    // 404: không tồn tại, đã vào thùng rác hoặc của người khác; hỏi lại cũng vậy.
    retry: false,
  })
}

export function useWorkspaceDocuments(id: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.workspaceDocuments(id),
    queryFn: () => workspacesApi.documents(id),
    staleTime: 10_000,
    retry: false,
    enabled,
    refetchInterval: (current) =>
      current.state.data?.some((attached) => inProgress(attached.document)) ? PROCESSING_POLL_MS : false,
  })
}

/**
 * Các không gian học đang dùng tài liệu (hoặc tài liệu trong thư mục): cho khung thông tin tài liệu và lời báo trước
 * khi xóa. Luôn hỏi lại khi mở, vì đây là căn cứ để người học xác nhận xóa.
 */
export function useDocumentUsage(target: UsageTarget, enabled = true) {
  const ids = [...(target.documentIds ?? [])].sort()
  const hasTarget = ids.length > 0 || Boolean(target.folderId)
  return useQuery({
    queryKey: queryKeys.workspaceUsage({ ids, folderId: target.folderId ?? null }),
    queryFn: () => workspacesApi.usage({ documentIds: ids, folderId: target.folderId }),
    staleTime: 0,
    retry: false,
    enabled: enabled && hasTarget,
  })
}

/** Thao tác làm đổi không gian học: xong thì làm mới danh sách, chi tiết, tài liệu đã gắn và thư mục của kho. */
export function useWorkspaceMutation<TInput, TResult>(action: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: action,
    onSettled: () => client.invalidateQueries({ queryKey: queryKeys.workspaces }),
  })
}

/** Dàn ý chủ đề của không gian học. */
export function useTopics(workspaceId: string) {
  return useQuery({
    queryKey: queryKeys.workspaceTopics(workspaceId),
    queryFn: () => workspacesApi.topics(workspaceId),
    staleTime: 30_000,
    retry: false,
  })
}

/**
 * Thao tác trên dàn ý: máy chủ trả lại cả cây nên đặt thẳng vào bộ nhớ đệm; lỗi (ví dụ vượt ba cấp) thì giữ nguyên cây
 * cũ để chủ đề trở về chỗ cũ.
 */
export function useTopicMutation<TInput>(workspaceId: string, action: (input: TInput) => Promise<TopicTree>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: action,
    onSuccess: (tree) => {
      client.setQueryData(queryKeys.workspaceTopics(workspaceId), tree)
      void client.invalidateQueries({ queryKey: queryKeys.quota })
    },
  })
}
