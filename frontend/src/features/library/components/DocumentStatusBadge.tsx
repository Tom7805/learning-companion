import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import type { DocumentStatus } from '../types'

const tones: Record<DocumentStatus, string> = {
  PENDING: 'bg-surface text-ink',
  PROCESSING: 'bg-warning-soft text-warning',
  READY: '',
  FAILED: 'bg-danger-soft text-danger',
}

/** Trạng thái xử lý; tài liệu đã sẵn sàng thì không cần nhắc nên không hiện gì. */
export function DocumentStatusBadge({ status, always = false }: { status?: DocumentStatus; always?: boolean }) {
  const { t } = useTranslation()
  if (!status || (status === 'READY' && !always)) return null
  return (
    <span
      data-testid="document-status"
      className={cn(
        'inline-flex items-center rounded-lg border-[1.5px] border-ink px-2 py-0.5 text-xs font-medium',
        tones[status] || 'bg-success-soft text-success',
      )}
    >
      {t(`library.status.${status}`)}
    </span>
  )
}
