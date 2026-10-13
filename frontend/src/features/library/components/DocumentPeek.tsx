import { ExternalLink } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import { Badge, ErrorState, Skeleton, buttonClasses } from '@/shared/ui'
import { useDocument } from '../api/queries'

/**
 * Xem nhanh một tài liệu trong khung xem bên (NCL-02-CN-001), để vừa xem vừa làm việc ở vùng chính. Tài liệu không
 * còn hoặc không thuộc về người học thì báo như mọi chỗ khác: không tìm thấy.
 */
export function DocumentPeek({ id, onOpenFull }: { id: string; onOpenFull: () => void }) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const document = useDocument(id)

  if (document.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-5 w-24" />
        <Skeleton className="h-40" />
      </div>
    )
  }
  if (document.isError) {
    const notFound = toApiError(document.error).status === 404
    return (
      <ErrorState
        title={notFound ? t('library.notFound.title') : t('library.loadFailed')}
        description={notFound ? t('library.notFound.body') : undefined}
        onRetry={notFound ? undefined : () => void document.refetch()}
      />
    )
  }

  const doc = document.data
  return (
    <article className="flex flex-col gap-4" data-testid="document-peek">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="ink">{t(`library.type.${doc.type}`)}</Badge>
        <span className="text-[0.8125rem] text-muted">
          {format.dateTime(doc.createdAt)} · {formatBytes(doc.sizeBytes, i18n.language)}
        </span>
      </div>
      <div className="whitespace-pre-wrap text-[0.9375rem] leading-7 text-ink-soft">
        {doc.contentText || t('library.noContent')}
      </div>
      <Link to={documentPath(doc.id)} onClick={onOpenFull} className={buttonClasses('outline', 'md', 'self-start')}>
        <ExternalLink className="size-4" aria-hidden="true" />
        {t('library.peek.openFull')}
      </Link>
    </article>
  )
}
