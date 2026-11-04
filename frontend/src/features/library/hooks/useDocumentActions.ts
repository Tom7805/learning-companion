import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { toast } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { useLibraryMutation } from '../api/queries'
import type { BulkRequest } from '../types'

/**
 * Các thao tác trên tài liệu dùng chung cho danh sách, thanh chọn nhiều và khung thông tin: gọi máy chủ, làm mới kho
 * và báo kết quả bằng thông báo nổi. Lỗi cần hiện trong hộp thoại (tên trùng) được trả về cho nơi gọi.
 */
export function useDocumentActions() {
  const { t } = useTranslation()
  const bulk = useLibraryMutation((request: BulkRequest) => libraryApi.bulk(request))
  const star = useLibraryMutation(({ id, starred }: { id: string; starred: boolean }) =>
    libraryApi.starDocument(id, starred),
  )
  const rename = useLibraryMutation(({ id, title }: { id: string; title: string }) =>
    libraryApi.renameDocument(id, title),
  )
  const setTags = useLibraryMutation(({ id, tagIds }: { id: string; tagIds: string[] }) =>
    libraryApi.setDocumentTags(id, tagIds),
  )

  const failed = (error: unknown): ApiError => {
    const apiError = toApiError(error)
    toast.error(t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }))
    return apiError
  }

  return {
    pending: bulk.isPending || star.isPending || rename.isPending || setTags.isPending,

    /** Chuyển tài liệu vào thư mục ({@code null}: thư mục gốc). Trả về lỗi nếu có tên trùng để hộp thoại hiện. */
    async move(documentIds: string[], folderId: string | null, folderName: string): Promise<ApiError | null> {
      try {
        const result = await bulk.mutateAsync({ action: 'MOVE', documentIds, folderId })
        toast.success(t('library.moveDialog.moved', { count: result.affected, folder: folderName }))
        return null
      } catch (error) {
        const apiError = toApiError(error)
        if (apiError.code !== 'LIBRARY_NAME_TAKEN') failed(error)
        else toast.error(conflictMessage(apiError, t))
        return apiError
      }
    },

    async trash(documentIds: string[]) {
      try {
        const result = await bulk.mutateAsync({ action: 'TRASH', documentIds })
        toast.success(t('library.deleteDocuments.done', { count: result.affected }))
        return true
      } catch (error) {
        failed(error)
        return false
      }
    },

    async starMany(documentIds: string[], starred: boolean) {
      try {
        await bulk.mutateAsync({ action: starred ? 'STAR' : 'UNSTAR', documentIds })
        return true
      } catch (error) {
        failed(error)
        return false
      }
    },

    async toggleStar(id: string, title: string, starred: boolean) {
      try {
        await star.mutateAsync({ id, starred })
        toast.success(t(starred ? 'library.starredToast' : 'library.unstarredToast', { title }))
      } catch (error) {
        failed(error)
      }
    },

    async rename(id: string, title: string): Promise<ApiError | null> {
      try {
        const updated = await rename.mutateAsync({ id, title })
        toast.success(t('library.renameDocument.done', { name: updated.title }))
        return null
      } catch (error) {
        const apiError = toApiError(error)
        if (apiError.code !== 'LIBRARY_NAME_TAKEN' && apiError.code !== 'VALIDATION_FAILED') failed(error)
        return apiError
      }
    },

    async setTags(id: string, tagIds: string[]) {
      try {
        await setTags.mutateAsync({ id, tagIds })
        toast.success(t('library.tags.done'))
        return true
      } catch (error) {
        failed(error)
        return false
      }
    },

    async tagMany(documentIds: string[], add: string[], remove: string[]) {
      try {
        if (add.length) await bulk.mutateAsync({ action: 'ADD_TAGS', documentIds, tagIds: add })
        if (remove.length) await bulk.mutateAsync({ action: 'REMOVE_TAGS', documentIds, tagIds: remove })
        toast.success(t('library.tags.done'))
        return true
      } catch (error) {
        failed(error)
        return false
      }
    },
  }
}

export function conflictMessage(error: ApiError, t: TFunction) {
  const names = error.details?.conflicts
  return Array.isArray(names) && names.length
    ? t('library.moveDialog.conflicts', { names: names.map((name) => `“${name}”`).join(', ') })
    : t('errors.LIBRARY_NAME_TAKEN')
}
