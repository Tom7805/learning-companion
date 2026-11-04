import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { DocumentCard, LibraryQuery } from '../types'
import { libraryApi } from './libraryApi'

/**
 * Mọi tài liệu (dùng cho ô mở nhanh). {@code enabled} false: không hỏi máy chủ, ví dụ khi người đăng nhập là quản trị
 * viên (không có thư viện).
 */
export function useDocuments(enabled = true) {
  return useQuery({ queryKey: queryKeys.documents, queryFn: () => libraryApi.documents(), staleTime: 30_000, enabled })
}

/** Hỏi lại máy chủ sau mỗi chừng này khi còn tài liệu chờ hoặc đang xử lý, để trạng thái tự cập nhật. */
export const PROCESSING_POLL_MS = 3_000

const inProgress = (document?: Pick<DocumentCard, 'status'>) =>
  document?.status === 'PENDING' || document?.status === 'PROCESSING'

/** Danh sách trong kho theo phạm vi và bộ lọc; giữ danh sách cũ trong lúc tải bộ lọc mới để trang không nháy. */
export function useLibraryDocuments(query: LibraryQuery) {
  return useQuery({
    queryKey: queryKeys.libraryDocuments(query),
    queryFn: () => libraryApi.documents(query),
    staleTime: 15_000,
    placeholderData: keepPreviousData,
    // 404: thư mục không tồn tại, đã vào thùng rác hoặc của người khác; hỏi lại cũng vậy.
    retry: false,
    refetchInterval: (current) => (current.state.data?.some(inProgress) ? PROCESSING_POLL_MS : false),
  })
}

export function useDocument(id: string) {
  return useQuery({
    queryKey: queryKeys.document(id),
    queryFn: () => libraryApi.document(id),
    staleTime: 30_000,
    // 404 nghĩa là không có quyền hoặc không tồn tại: hỏi lại cũng vậy.
    retry: false,
    // Tài liệu đang chờ hay đang xử lý: hỏi lại để trạng thái tự cập nhật.
    refetchInterval: (current) => (inProgress(current.state.data) ? PROCESSING_POLL_MS : false),
  })
}

/** Chi tiết cho khung thông tin: đọc không tính là một lần mở tài liệu. */
export function useDocumentInfo(id: string) {
  return useQuery({
    queryKey: [...queryKeys.document(id), 'info'],
    queryFn: () => libraryApi.document(id, false),
    staleTime: 10_000,
    retry: false,
    refetchInterval: (current) => (inProgress(current.state.data) ? PROCESSING_POLL_MS : false),
  })
}

export function useFolderTree() {
  return useQuery({ queryKey: queryKeys.folderTree, queryFn: libraryApi.folders, staleTime: 15_000 })
}

export function useTags() {
  return useQuery({ queryKey: queryKeys.tags, queryFn: libraryApi.tags, staleTime: 30_000 })
}

/** Thao tác làm đổi kho tài liệu: xong thì làm mới cây thư mục, danh sách, nhãn và chi tiết tài liệu. */
export function useLibraryMutation<TInput, TResult>(action: (input: TInput) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: action,
    onSettled: () => client.invalidateQueries({ queryKey: queryKeys.library }),
  })
}
