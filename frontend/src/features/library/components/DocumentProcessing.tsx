import { CircleAlert, LoaderCircle, RotateCcw } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Alert, Button } from '@/shared/ui'
import { useReprocess } from '../hooks/useReprocess'
import { canRetry, processingErrorMessage, processingSummary } from '../lib/processing'
import type { DocumentCard } from '../types'

type ProcessedDocument = Pick<DocumentCard, 'id' | 'title' | 'type' | 'status' | 'processing'>

/**
 * Lỗi xử lý của một tài liệu ngay trên thẻ trong kho (NCL-05-CN-003): lý do bằng lời, kèm nút "Thử lại" khi lỗi do
 * dịch vụ tạm thời, hoặc gợi ý tải bản khác khi lỗi do chính tệp. Nằm ngoài liên kết mở tài liệu để nút bấm được.
 */
export function ProcessingFailure({ document, className }: { document: ProcessedDocument; className?: string }) {
  const { t } = useTranslation()
  const reprocess = useReprocess()
  const message = processingErrorMessage(document, t)
  if (!message) return null
  const retryable = canRetry(document)
  return (
    <div
      data-testid="processing-error"
      className={cn('flex flex-wrap items-center gap-x-3 gap-y-2 text-[0.8125rem] leading-snug', className)}
    >
      <span className="flex min-w-0 flex-1 basis-48 items-start gap-1.5">
        <CircleAlert className="mt-0.5 size-4 shrink-0 text-danger" aria-hidden="true" />
        <span className="[overflow-wrap:anywhere]">{message}</span>
      </span>
      {retryable && (
        <Button
          variant="outline"
          className="h-9 px-3 text-[0.8125rem]"
          loading={reprocess.pendingId === document.id}
          icon={<RotateCcw className="size-4" aria-hidden="true" />}
          aria-label={t('library.processing.retryNamed', { title: document.title })}
          data-testid="processing-retry"
          onClick={() => void reprocess.retry(document)}
        >
          {t('library.processing.retry')}
        </Button>
      )}
    </div>
  )
}

/**
 * Trạng thái xử lý đầy đủ trên trang tài liệu và khung thông tin: đang chờ hay đang xử lý (tự cập nhật), lỗi kèm lý do
 * và nút thử lại, hoặc tóm tắt số trang và số đoạn đã lập chỉ mục.
 */
export function ProcessingPanel({ document }: { document: ProcessedDocument }) {
  const { t } = useTranslation()
  const reprocess = useReprocess()
  if (document.status === 'PENDING' || document.status === 'PROCESSING') {
    return (
      <Alert tone="info" data-testid="processing-state">
        <span className="flex items-center gap-2">
          <LoaderCircle className="size-4 shrink-0 animate-spin motion-reduce:animate-none" aria-hidden="true" />
          {t(`library.processing.${document.status === 'PENDING' ? 'pending' : 'running'}`)}
        </span>
      </Alert>
    )
  }
  if (document.status === 'FAILED') {
    const retryable = canRetry(document)
    return (
      <Alert tone="danger" data-testid="processing-state">
        <p className="font-medium">{t('library.processing.failedTitle')}</p>
        <p data-testid="processing-error">{processingErrorMessage(document, t)}</p>
        {retryable ? (
          <Button
            variant="outline"
            className="mt-3"
            loading={reprocess.pendingId === document.id}
            icon={<RotateCcw className="size-4" aria-hidden="true" />}
            data-testid="processing-retry"
            onClick={() => void reprocess.retry(document)}
          >
            {t('library.processing.retry')}
          </Button>
        ) : (
          <p className="mt-1 text-ink-soft">{t('library.processing.uploadAnother')}</p>
        )}
      </Alert>
    )
  }
  const summary = processingSummary(document, t)
  return summary ? (
    <p className="text-[0.875rem] text-ink-soft" data-testid="processing-summary">
      {summary}
    </p>
  ) : null
}
