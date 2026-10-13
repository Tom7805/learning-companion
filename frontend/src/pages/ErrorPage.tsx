import { useTranslation } from 'react-i18next'
import { useRouteError } from 'react-router'
import { reloadPage } from '@/shared/lib/navigation'
import { ErrorState } from '@/shared/ui'

/** Lỗi khi tải gói mã của trang: mất mạng giữa chừng, hoặc bản mới vừa thay bản đang mở. */
function isChunkLoadError(error: unknown) {
  return (
    error instanceof Error &&
    /dynamically imported module|Importing a module script failed|Failed to fetch|error loading dynamically/i.test(
      error.message,
    )
  )
}

/**
 * Trang không mở được vì lỗi bất ngờ: báo trong vùng nội dung, phần khung ứng dụng vẫn ở nguyên để đi trang khác, và
 * cho tải lại (NCL-02-CN-001).
 */
export function ErrorPage() {
  const { t } = useTranslation()
  const error = useRouteError()
  const network = isChunkLoadError(error) || !navigator.onLine
  return (
    <div className="grid min-h-[50dvh] place-items-center py-6">
      <ErrorState
        title={t('routeError.title')}
        description={network ? t('errors.NETWORK_ERROR') : t('errors.INTERNAL_ERROR')}
        onRetry={reloadPage}
        className="w-full max-w-md"
      />
    </div>
  )
}
