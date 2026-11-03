import { ChevronDown, CircleAlert, CircleCheck, ShieldX } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { cn } from '@/shared/lib/cn'
import type { AuditLogEntry, AuditOutcome } from '../types'

export const AUDIT_COLUMNS = 'lg:grid-cols-[10.5rem_minmax(0,1.3fr)_minmax(0,1.4fr)_minmax(0,1.1fr)_minmax(0,1fr)_2.75rem]'

const outcomeStyle: Record<AuditOutcome, { icon: typeof CircleCheck; className: string }> = {
  SUCCESS: { icon: CircleCheck, className: 'bg-success-soft text-ink' },
  FAILURE: { icon: CircleAlert, className: 'bg-warning-soft text-ink' },
  DENIED: { icon: ShieldX, className: 'bg-danger-soft text-danger' },
}

/**
 * Một dòng nhật ký: ai, làm gì, với đối tượng nào, lúc nào, từ thiết bị nào và kết quả ra sao. Mở rộng để xem mã
 * dòng, mã yêu cầu và chi tiết ghi kèm (chỉ siêu dữ liệu, trường nội dung đã bị loại từ lúc ghi).
 */
export function AuditLogRow({ entry }: { entry: AuditLogEntry }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const [open, setOpen] = useState(false)
  const detailsId = useId()
  const outcome = outcomeStyle[entry.outcome]
  const actionLabel = t(`audit.actions.${entry.action}`, { defaultValue: entry.action })

  const actor =
    entry.actor?.email ??
    (entry.actorType === 'ANONYMOUS'
      ? t('audit.actor.anonymous')
      : entry.actorType === 'SYSTEM'
        ? t('audit.actor.system')
        : t('audit.actor.removed'))
  const device = entry.device
    ? t('audit.device', { browser: entry.device.browser, os: entry.device.operatingSystem })
    : t('audit.noDevice')

  return (
    <li data-testid="audit-row" data-action={entry.action} className="py-3.5">
      <div className={cn('grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1.5 lg:items-center lg:gap-4', AUDIT_COLUMNS)}>
        <p className="text-[0.8125rem] text-muted tabular-nums lg:text-sm lg:text-ink">
          <time dateTime={entry.occurredAt}>{format.dateTime(entry.occurredAt)}</time>
        </p>
        <div className="min-w-0">
          <p className="truncate font-medium" title={entry.actor?.email}>
            {actor}
          </p>
          {entry.actor && entry.actor.displayName && (
            <p className="truncate text-xs text-muted">{entry.actor.displayName}</p>
          )}
        </div>
        <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-medium">{actionLabel}</span>
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-semibold',
              outcome.className,
            )}
            data-testid="audit-outcome"
          >
            <outcome.icon className="size-3.5" aria-hidden="true" />
            {t(`audit.outcomes.${entry.outcome}`)}
          </span>
          <span className="w-full text-xs text-muted">{t(`audit.categories.${entry.category}`)}</span>
        </div>
        <div className="col-span-2 min-w-0 text-sm lg:col-span-1">
          <p className="truncate">
            <span className="text-muted lg:hidden">{t('audit.columns.object')}: </span>
            {t(`audit.objectTypes.${entry.objectType}`, { defaultValue: entry.objectType })}
          </p>
          {entry.subject ? (
            <p className="truncate text-xs text-muted" title={entry.subject.email}>
              {entry.subject.email}
            </p>
          ) : (
            entry.objectId && <p className="truncate font-mono text-xs text-muted">{entry.objectId.slice(0, 8)}</p>
          )}
        </div>
        <div className="col-span-2 min-w-0 text-sm lg:col-span-1">
          <p className="truncate">
            <span className="text-muted lg:hidden">{t('audit.columns.device')}: </span>
            {device}
          </p>
          {entry.ipAddress && <p className="truncate font-mono text-xs text-muted">{entry.ipAddress}</p>}
        </div>
        {/* Một nút cho mọi cỡ màn hình: góc phải trên của thẻ trên điện thoại, cột cuối của bảng trên máy tính. */}
        <div className="col-start-2 row-span-2 row-start-1 flex justify-end lg:col-start-6 lg:row-span-1">
          <ToggleButton open={open} onToggle={() => setOpen(!open)} controls={detailsId} action={actionLabel} />
        </div>
      </div>
      {open && <AuditDetails id={detailsId} entry={entry} />}
    </li>
  )
}

function ToggleButton({
  open,
  onToggle,
  controls,
  action,
}: {
  open: boolean
  onToggle: () => void
  controls: string
  action: string
}) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      aria-expanded={open}
      aria-controls={controls}
      aria-label={t(open ? 'audit.hideDetails' : 'audit.showDetails', { action })}
      onClick={onToggle}
      className="grid size-11 place-items-center rounded-full outline-none transition-colors hover:bg-ink/5 focus-visible:ring-4 focus-visible:ring-focus/25"
      data-testid="audit-toggle"
    >
      <ChevronDown className={cn('size-5 transition-transform', open && 'rotate-180')} aria-hidden="true" />
    </button>
  )
}

function AuditDetails({ id, entry }: { id: string; entry: AuditLogEntry }) {
  const { t } = useTranslation()
  const { changes, removedFields, ...rest } = entry.details as {
    changes?: { field: string; before: unknown; after: unknown }[]
    removedFields?: string[]
  } & Record<string, unknown>
  // Mã và địa chỉ dùng phông đơn cách cho dễ đọc từng ký tự; chữ thường như loại thiết bị thì không.
  const facts: [string, string | null | undefined, boolean][] = [
    [t('audit.facts.id'), entry.id, true],
    [t('audit.facts.actorId'), entry.actorId, true],
    [t('audit.facts.subject'), entry.subject?.email, true],
    [t('audit.facts.objectId'), entry.objectId, true],
    [t('audit.facts.ip'), entry.ipAddress, true],
    [t('audit.facts.deviceType'), entry.device ? t(`devices.type.${entry.device.deviceType}`) : null, false],
    [t('audit.facts.requestId'), entry.requestId, true],
  ]
  const extra = Object.entries(rest)

  return (
    <div
      id={id}
      className="mt-3 grid gap-4 rounded-field bg-paper p-4 text-sm lg:grid-cols-2"
      data-testid="audit-details"
    >
      {/* Điện thoại hẹp: nhãn nằm trên giá trị để mã dài không bị bóp thành nhiều dòng. */}
      <dl className="grid gap-y-0.5 sm:grid-cols-[auto_minmax(0,1fr)] sm:gap-x-4 sm:gap-y-1.5">
        {facts
          .filter(([, value]) => value)
          .map(([label, value, mono]) => (
            <div key={label} className="contents">
              <dt className="text-xs text-muted sm:text-sm">{label}</dt>
              <dd className={cn('mb-1.5 break-all sm:mb-0', mono && 'font-mono text-[0.8125rem]')}>{value}</dd>
            </div>
          ))}
      </dl>
      <div className="flex flex-col gap-3">
        {changes && changes.length > 0 && (
          <div>
            <p className="font-medium">{t('audit.changes')}</p>
            <ul className="mt-1 flex flex-col gap-1" data-testid="audit-changes">
              {changes.map((change) => (
                <li key={change.field}>
                  <span className="font-mono text-[0.8125rem]">{change.field}</span>:{' '}
                  {t('audit.changeValue', { before: display(change.before), after: display(change.after) })}
                </li>
              ))}
            </ul>
          </div>
        )}
        {extra.length > 0 && (
          <div>
            <p className="font-medium">{t('audit.extra')}</p>
            <dl className="mt-1 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1">
              {extra.map(([key, value]) => (
                <div key={key} className="contents">
                  <dt className="font-mono text-[0.8125rem] text-muted">{key}</dt>
                  <dd className="break-all font-mono text-[0.8125rem]">{display(value)}</dd>
                </div>
              ))}
            </dl>
          </div>
        )}
        {removedFields && removedFields.length > 0 && (
          <p className="rounded-lg bg-warning-soft px-3 py-2" data-testid="audit-removed">
            {t('audit.removed', { fields: removedFields.join(', ') })}
          </p>
        )}
        {!changes && extra.length === 0 && !removedFields && <p className="text-muted">{t('audit.noExtra')}</p>}
      </div>
    </div>
  )
}

function display(value: unknown) {
  if (value === null || value === undefined) return '—'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
