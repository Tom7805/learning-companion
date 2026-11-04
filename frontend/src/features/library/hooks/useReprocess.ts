import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { toast } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { useLibraryMutation } from '../api/queries'
import type { DocumentCard } from '../types'

/** Thử xử lý lại tài liệu lỗi: tài liệu quay về hàng chờ, danh sách tự cập nhật khi xử lý xong. */
export function useReprocess() {
  const { t } = useTranslation()
  const mutation = useLibraryMutation((id: string) => libraryApi.reprocess(id))
  return {
    pendingId: mutation.isPending ? mutation.variables : undefined,
    async retry(document: Pick<DocumentCard, 'id' | 'title'>) {
      try {
        await mutation.mutateAsync(document.id)
        toast.success(t('library.processing.requeued', { title: document.title }))
      } catch (error) {
        const apiError = toApiError(error)
        toast.error(t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }))
      }
    },
  }
}
