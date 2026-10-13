import { CalendarClock, Check, Clock3, Download, FileArchive, Hourglass, Sparkles } from 'lucide-react'
import { useId, useRef, useState, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { dataExportPath } from '@/app/routes'
import { useAuth } from '@/features/auth'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Button, CtaButton, Skeleton, Spinner, buttonClasses } from '@/shared/ui'
import { useDataExportOverview, useRequestDataExport } from '../api/queries'
import type { DataExport } from '../types'

const INCLUDED = ['account', 'profile', 'privacy', 'library'] as const

/**
 * Xuất toàn bộ dữ liệu học tập (NCL-01-CN-008) trong mục Tài khoản của cài đặt: yêu cầu xuất, theo dõi lúc gói
 * đang được dựng ở luồng nền, rồi sang trang tải khi gói sẵn sàng. Mỗi ngày một yêu cầu.
 */
export function DataExportSection({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation()
  const titleId = useId()
  const { account } = useAuth()
  const overview = useDataExportOverview()
  const request = useRequestDataExport()
  const progress = useRef<HTMLDivElement>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null)
  const format = useDateFormat()

  const requestExport = async () => {
    setNotice(null)
    try {
      await request.mutateAsync()
      setNotice({ tone: 'success', text: t('dataExport.requested', { email: account?.email ?? '' }) })
      requestAnimationFrame(() => progress.current?.focus())
    } catch (caught) {
      const error = toApiError(caught)
      const next = typeof error.details?.nextAvailableAt === 'string' ? error.details.nextAvailableAt : undefined
      const text = t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })
      setNotice({
        tone: 'danger',
        text: next ? `${text} ${t('dataExport.nextAt', { time: format.dateTime(next) })}` : text,
      })
      if (error.code === 'EXPORT_DAILY_LIMIT') void overview.refetch()
    }
  }

  const latest = overview.data?.latest ?? undefined
  const busy = latest?.status === 'QUEUED' || latest?.status === 'RUNNING'

  return (
    <section
      aria-labelledby={titleId}
      data-testid="data-export"
      className="flex flex-col gap-5 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
    >
      <div className="flex items-start gap-4">
        <span
          aria-hidden="true"
          className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
        >
          <FileArchive className="size-6" />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <h3 id={titleId} className="text-lg font-semibold">
            {t('dataExport.title')}
          </h3>
          <p className="text-sm leading-relaxed text-ink-soft">{t('dataExport.subtitle')}</p>
        </div>
      </div>

      <div className="flex flex-col gap-2 rounded-2xl bg-paper p-4">
        <p className="text-sm font-medium">{t('dataExport.includesTitle')}</p>
        <ul className="grid gap-x-4 gap-y-1.5 text-sm text-ink-soft sm:grid-cols-2">
          {INCLUDED.map((item) => (
            <li key={item} className="flex items-start gap-2">
              <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
              {t(`dataExport.includes.${item}`)}
            </li>
          ))}
        </ul>
        <p className="text-[0.8125rem] leading-snug text-muted">
          {t('dataExport.includesMore')} {t('dataExport.formats')}.
        </p>
      </div>

      <ul className="flex flex-wrap gap-2">
        {[
          { icon: CalendarClock, text: t('dataExport.facts.daily') },
          { icon: Sparkles, text: t('dataExport.facts.quota') },
          { icon: Clock3, text: t('dataExport.facts.validity') },
        ].map((fact) => (
          <li
            key={fact.text}
            className="inline-flex items-center gap-1.5 rounded-full border-[1.5px] border-ink/15 bg-surface px-3 py-1 text-[0.8125rem] text-ink-soft"
          >
            <fact.icon className="size-3.5" aria-hidden="true" />
            {fact.text}
          </li>
        ))}
      </ul>

      {/* Lời báo "đã nhận yêu cầu" chỉ cần trong lúc chờ; gói xong thì khung sẵn sàng thay cho nó. */}
      {notice && (notice.tone === 'danger' || busy) && (
        <Alert tone={notice.tone} data-testid="export-notice" key={notice.text}>
          {notice.text}
        </Alert>
      )}

      {overview.isPending && <Skeleton className="h-20" />}
      {overview.isError && (
        <Alert tone="danger">
          <p>{t('dataExport.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void overview.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {overview.data && (
        <>
          {latest && <LatestExport export={latest} progressRef={progress} onNavigate={onNavigate} />}
          {!busy &&
            (overview.data.canRequest ? (
              latest?.status === 'READY' ? (
                <Button
                  variant="outline"
                  className="self-start"
                  loading={request.isPending}
                  onClick={() => void requestExport()}
                >
                  {t('dataExport.requestAgain')}
                </Button>
              ) : (
                <CtaButton
                  loading={request.isPending}
                  hint={t('dataExport.requestHint')}
                  onClick={() => void requestExport()}
                >
                  {latest ? t('dataExport.requestAgain') : t('dataExport.request')}
                </CtaButton>
              )
            ) : (
              overview.data.nextAvailableAt && (
                <p className="flex items-center gap-2 text-sm text-muted" data-testid="export-next">
                  <CalendarClock className="size-4 shrink-0" aria-hidden="true" />
                  {t('dataExport.nextAt', { time: format.dateTime(overview.data.nextAvailableAt) })}
                </p>
              )
            ))}
        </>
      )}
    </section>
  )
}

function LatestExport({
  export: latest,
  progressRef,
  onNavigate,
}: {
  export: DataExport
  progressRef: RefObject<HTMLDivElement | null>
  onNavigate: () => void
}) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()

  if (latest.status === 'QUEUED' || latest.status === 'RUNNING') {
    return (
      <div
        ref={progressRef}
        tabIndex={-1}
        role="status"
        data-testid="export-progress"
        className="flex items-center gap-4 rounded-2xl border-[1.5px] border-ink bg-lilac-soft p-4 outline-none focus-visible:ring-4 focus-visible:ring-focus/25"
      >
        <span className="grid size-10 shrink-0 place-items-center rounded-full border-[1.5px] border-ink bg-surface">
          <Spinner className="size-5" />
        </span>
        <div className="flex flex-col">
          <p className="font-medium">{t('dataExport.preparing')}</p>
          <p className="text-sm text-ink-soft">{t('dataExport.preparingHint')}</p>
        </div>
      </div>
    )
  }

  if (latest.status === 'READY') {
    return (
      <div
        role="status"
        data-testid="export-ready"
        className="flex flex-col gap-4 rounded-2xl border-[1.5px] border-ink bg-sky-soft p-4 sm:flex-row sm:items-center"
      >
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <p className="font-medium">{t('dataExport.readyTitle')}</p>
          <p className="text-sm text-ink-soft">
            {t('dataExport.readyMeta', {
              files: latest.fileCount ?? 0,
              size: formatBytes(latest.sizeBytes ?? 0, i18n.language),
              time: format.dateTime(latest.completedAt ?? latest.requestedAt),
            })}
          </p>
          {latest.expiresAt && (
            <p className="text-[0.8125rem] text-muted">{t('dataExport.readyUntil', { time: format.dateTime(latest.expiresAt) })}</p>
          )}
        </div>
        <Link to={dataExportPath(latest.id)} onClick={onNavigate} className={buttonClasses('dark', 'md', 'shrink-0')}>
          <Download className="size-4" aria-hidden="true" />
          {t('dataExport.download')}
        </Link>
      </div>
    )
  }

  if (latest.status === 'FAILED') {
    return (
      <Alert tone="danger" data-testid="export-failed">
        <p className="font-medium">{t('dataExport.failedTitle')}</p>
        <p>{t('dataExport.failedBody')}</p>
      </Alert>
    )
  }

  return (
    <div data-testid="export-expired" className="flex items-start gap-3 rounded-2xl bg-paper p-4 text-sm">
      <Hourglass className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden="true" />
      <div>
        <p className="font-medium">
          {t('dataExport.expiredTitle', { time: format.dateTime(latest.completedAt ?? latest.requestedAt) })}
        </p>
        <p className="text-muted">{t('dataExport.expiredBody')}</p>
      </div>
    </div>
  )
}
