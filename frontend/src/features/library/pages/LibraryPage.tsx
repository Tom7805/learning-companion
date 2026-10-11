import { BookOpen } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Button, Skeleton } from '@/shared/ui'
import { useDocuments } from '../api/queries'
import { DocumentGrid } from '../components/DocumentGrid'
import { PrivacyNote } from '../components/PrivacyNote'

/** Thư viện của người học: chỉ liệt kê tài liệu của chính người đang đăng nhập (NCL-01-CN-004). */
export function LibraryPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('library.title'))
  const documents = useDocuments()
  const totalBytes = documents.data?.reduce((sum, document) => sum + document.sizeBytes, 0) ?? 0

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex max-w-xl flex-col gap-2">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('library.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('library.subtitle')}</p>
        </div>
        {documents.data && documents.data.length > 0 && (
          <p className="text-sm font-medium tabular-nums" data-testid="library-summary">
            {t('library.summary', {
              count: documents.data.length,
              size: formatBytes(totalBytes, i18n.language),
            })}
          </p>
        )}
      </header>

      <PrivacyNote />

      {documents.isPending && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy="true">
          <Skeleton className="h-40" />
          <Skeleton className="h-40" />
          <Skeleton className="hidden h-40 xl:block" />
        </div>
      )}
      {documents.isError && (
        <Alert tone="danger">
          <p>{t('library.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void documents.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {documents.data && documents.data.length > 0 && <DocumentGrid documents={documents.data} />}
      {documents.data?.length === 0 && (
        <div
          data-testid="library-empty"
          className="flex flex-col items-center gap-3 rounded-card border-[1.5px] border-dashed border-ink/50 px-6 py-12 text-center"
        >
          <span
            aria-hidden="true"
            className="grid size-14 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
          >
            <BookOpen className="size-6" />
          </span>
          <h2 className="text-xl font-medium">{t('library.empty.title')}</h2>
          <p className="max-w-md text-sm leading-relaxed text-muted">{t('library.empty.body')}</p>
        </div>
      )}
    </div>
  )
}
