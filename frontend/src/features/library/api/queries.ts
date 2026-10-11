import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { libraryApi } from './libraryApi'

export function useDocuments() {
  return useQuery({ queryKey: queryKeys.documents, queryFn: libraryApi.documents, staleTime: 30_000 })
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
