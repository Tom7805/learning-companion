import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { libraryApi } from './libraryApi'

/** {@code enabled} false: không hỏi máy chủ, ví dụ khi người đăng nhập là quản trị viên (không có thư viện). */
export function useDocuments(enabled = true) {
  return useQuery({ queryKey: queryKeys.documents, queryFn: libraryApi.documents, staleTime: 30_000, enabled })
}

export function useDocument(id: string) {
  return useQuery({
    queryKey: queryKeys.document(id),
    queryFn: () => libraryApi.document(id),
    staleTime: 30_000,
    // 404 nghĩa là không có quyền hoặc không tồn tại: hỏi lại cũng vậy.
    retry: false,
  })
}
