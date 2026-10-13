import { ArrowLeft, FileQuestion, Lock } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { documentPath, routes } from '@/app/routes'
import { recentStore } from '@/app/store/recentStore'
import { useAuth } from '@/features/auth'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Badge, Button, Skeleton, buttonClasses } from '@/shared/ui'
import { useDocument } from '../api/queries'
import { PrivacyNote } from '../components/PrivacyNote'
import { DocumentViewer } from '../components/viewer/DocumentViewer'

/**
 * Một tài liệu kèm toàn bộ nội dung. Tài liệu của người khác hoặc mã không tồn tại đều hiện
 * cùng một màn hình "không tìm thấy", nên không ai dò được mã nào đang có dữ liệu.
 */
export function DocumentPage() {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const { id = '' } = useParams()
  const document = useDocument(id)
  const notFound = document.isError && toApiError(document.error).status === 404
  const { account } = useAuth()
  const accountId = account?.id
  const opened = document.data

  // Mục "Gần đây" của thanh bên: ghi tài liệu vừa mở; tài liệu không còn mở được thì bỏ khỏi danh sách.
  useEffect(() => {
    if (!accountId) return
    if (opened) {
      recentStore.record(accountId, { kind: 'document', id: opened.id, title: opened.title, to: documentPath(opened.id) })
    } else if (notFound) {
      recentStore.forget(accountId, 'document', id)
    }
  }, [accountId, opened, notFound, id])
  const heading = usePageHeading(
    document.data?.title ?? (notFound ? t('library.notFound.title') : t('library.title')),
    document.status,
  )

  if (document.isPending) {
    return (
      <div className="flex flex-col gap-5" aria-busy="true">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-64" />
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center gap-4 py-10 text-center" data-testid="document-not-found">
        <span aria-hidden="true" className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
          <FileQuestion className="size-7" />
        </span>
        <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
          {t('library.notFound.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('library.notFound.body')}</p>
        <Link to={routes.library} className={buttonClasses('dark', 'lg')}>
          {t('library.backToLibrary')}
        </Link>
      </div>
    )
  }

  if (document.isError) {
    return (
      <Alert tone="danger">
        <p>{t('library.loadFailed')}</p>
        <Button variant="outline" className="mt-3" onClick={() => void document.refetch()}>
          {t('common.retry')}
        </Button>
      </Alert>
    )
  }

  const doc = document.data
  return (
    <article className="flex flex-col gap-6">
      <Link
        to={routes.library}
        className="-my-2 inline-flex items-center gap-1.5 self-start py-2 text-sm font-medium text-ink-soft underline-offset-4 hover:text-ink hover:underline"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t('library.backToLibrary')}
      </Link>

      <header className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="ink">{t(`library.type.${doc.type}`)}</Badge>
          <Badge tone="lilac" className="gap-1">
            <Lock className="size-3.5" aria-hidden="true" />
            {t('library.onlyYou')}
          </Badge>
        </div>
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none [overflow-wrap:anywhere]">
          {doc.title}
        </h1>
        <p className="text-sm text-muted">
          {t('library.addedAt', { time: format.dateTime(doc.createdAt) })} ·{' '}
          {formatBytes(doc.sizeBytes, i18n.language)}
        </p>
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-6">
          <section
            aria-labelledby="document-content-title"
            className="rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
          >
            <h2 id="document-content-title" className="mb-3 text-lg font-medium">
              {t('library.content')}
            </h2>
            <div
              data-testid="document-content"
              className="whitespace-pre-wrap text-[0.9375rem] leading-7 text-ink-soft"
            >
              {doc.contentText || t('library.noContent')}
            </div>
          </section>
          <DocumentViewer document={doc} />
        </div>
        <PrivacyNote />
      </div>
    </article>
  )
}
