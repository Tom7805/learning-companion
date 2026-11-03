import { ScrollText } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { cn } from '@/shared/lib/cn'
import { deviceTimeZone, useDisplayTimeZone, zonedDayStart } from '@/shared/lib/timezone'
import { Alert, Button, EmptyState, Skeleton } from '@/shared/ui'
import type { AuditLogSearch } from '../api/adminApi'
import { useAuditLogs } from '../api/queries'
import { AuditLogFilters } from '../components/AuditLogFilters'
import { AUDIT_COLUMNS, AuditLogRow } from '../components/AuditLogRow'
import { isAuditCategory } from '../lib/auditCatalog'
import type { AuditLogFilters as Filters } from '../types'

const DATE = /^\d{4}-\d{2}-\d{2}$/
const FILTER_KEYS = ['account', 'category', 'action', 'objectType', 'from', 'to'] as const

function readFilters(params: URLSearchParams): Filters {
  const date = (key: string) => {
    const value = params.get(key)
    return value && DATE.test(value) ? value : undefined
  }
  const category = params.get('category')
  return {
    account: params.get('account') || undefined,
    category: isAuditCategory(category) ? category : undefined,
    action: params.get('action') || undefined,
    objectType: params.get('objectType') || undefined,
    from: date('from'),
    to: date('to'),
  }
}

function nextDay(date: string) {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10)
}

/** Ngày trên bộ lọc tính theo múi giờ hiển thị và lấy trọn ngày cuối: "đến 14/10" nghĩa là trước 0 giờ ngày 15/10. */
function toSearch(filters: Filters, timeZone: string): AuditLogSearch {
  return {
    account: filters.account,
    category: filters.category,
    action: filters.action,
    objectType: filters.objectType,
    from: filters.from ? zonedDayStart(filters.from, timeZone) : undefined,
    to: filters.to ? zonedDayStart(nextDay(filters.to), timeZone) : undefined,
  }
}

/**
 * Tra cứu nhật ký thao tác (NCL-03-CN-001): lọc theo tài khoản, loại thao tác, loại đối tượng và khoảng thời gian.
 * Bộ lọc nằm trên đường dẫn để tải lại hay gửi cho người khác vẫn ra đúng kết quả.
 */
export function AuditLogPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('audit.title'))
  const [params, setParams] = useSearchParams()
  const filters = useMemo(() => readFilters(params), [params])
  const timeZone = useDisplayTimeZone() ?? deviceTimeZone()
  const search = useMemo(() => toSearch(filters, timeZone), [filters, timeZone])
  const logs = useAuditLogs(search)
  const entries = logs.data?.pages.flatMap((page) => page.items) ?? []
  const filtered = Object.values(filters).some(Boolean)

  const apply = (next: Filters) => {
    const query = new URLSearchParams()
    for (const key of FILTER_KEYS) {
      const value = next[key]
      if (value) query.set(key, value)
    }
    setParams(query)
  }
  const loadError = logs.isError ? toApiError(logs.error) : null

  return (
    <div className="flex flex-col gap-6">
      <header className="flex max-w-2xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('audit.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('audit.subtitle')}</p>
      </header>

      <AuditLogFilters key={params.toString()} value={filters} onApply={apply} />

      <section
        aria-labelledby="audit-results-title"
        aria-busy={logs.isFetching || undefined}
        className="rounded-card border-[1.5px] border-ink bg-surface px-4 py-4 sm:px-6"
      >
        <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 pb-3">
          <h2 id="audit-results-title" className="text-lg font-medium">
            {t('audit.results')}
          </h2>
          {logs.data && (
            <p className="text-sm text-muted" role="status" data-testid="audit-count">
              {t(logs.hasNextPage ? 'audit.shownMore' : 'audit.shown', { count: entries.length })}
            </p>
          )}
        </div>
        <div
          aria-hidden="true"
          className={cn('hidden gap-4 border-b border-line pb-2 text-xs text-muted lg:grid', AUDIT_COLUMNS)}
        >
          <span>{t('audit.columns.time')}</span>
          <span>{t('audit.columns.actor')}</span>
          <span>{t('audit.columns.action')}</span>
          <span>{t('audit.columns.object')}</span>
          <span>{t('audit.columns.device')}</span>
          <span />
        </div>

        {logs.isPending && (
          <div className="flex flex-col gap-3 py-4" data-testid="audit-loading">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        )}
        {loadError && (
          <Alert tone="danger" className="my-4">
            <p>{loadError.status === 400 ? t('audit.invalidFilters') : t('audit.loadFailed')}</p>
            {loadError.status !== 400 && (
              <Button variant="outline" className="mt-3" onClick={() => void logs.refetch()}>
                {t('common.retry')}
              </Button>
            )}
          </Alert>
        )}
        {logs.data && entries.length === 0 && (
          <EmptyState
            icon={<ScrollText />}
            title={t(filtered ? 'audit.empty.filteredTitle' : 'audit.empty.title')}
            description={t(filtered ? 'audit.empty.filteredBody' : 'audit.empty.body')}
            action={
              filtered ? (
                <Button variant="outline" onClick={() => apply({})}>
                  {t('audit.filters.clear')}
                </Button>
              ) : undefined
            }
            className="my-4"
          />
        )}

        <ul className="divide-y divide-line" data-testid="audit-list">
          {entries.map((entry) => (
            <AuditLogRow key={entry.id} entry={entry} />
          ))}
        </ul>

        {logs.hasNextPage && (
          <div className="flex justify-center border-t border-line pt-4">
            <Button
              variant="outline"
              loading={logs.isFetchingNextPage}
              onClick={() => void logs.fetchNextPage()}
              data-testid="audit-load-more"
            >
              {t('audit.loadMore')}
            </Button>
          </div>
        )}
      </section>

      <p className="text-sm text-muted">{t('audit.retention')}</p>
    </div>
  )
}
