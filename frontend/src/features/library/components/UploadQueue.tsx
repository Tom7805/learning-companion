import { AlertTriangle, CheckCircle2, Copy, FileUp, Pencil, RotateCcw, WifiOff, X } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'
import { Button, buttonClasses } from '@/shared/ui'
import type { UploadItem } from '../hooks/useChunkedUpload'
import { ACTIVE_STATES } from '../lib/uploader'
import { statusText, uploadProblem } from '../lib/uploadText'

/**
 * Danh sách tệp đang tải lên (NCL-05-CN-002): mỗi tệp một thanh tiến độ, trạng thái bằng chữ (không chỉ bằng màu), lý
 * do khi bị từ chối, nút hủy, thử lại, đổi tên sau khi tải và mở bản đã có khi trùng. Kết quả từng tệp được đọc cho
 * trình đọc màn hình qua một vùng thông báo.
 */
export function UploadQueue({
  items,
  onCancel,
  onRetry,
  onRename,
  onShowDuplicate,
  onClear,
}: {
  items: UploadItem[]
  onCancel: (key: string) => void
  onRetry: (key: string) => void
  /** Không truyền thì không có nút đổi tên, ví dụ khi tải ngay tại không gian học. */
  onRename?: (item: UploadItem) => void
  /** Không truyền thì không có nút xem chi tiết bản trùng. */
  onShowDuplicate?: (item: UploadItem) => void
  onClear: () => void
}) {
  const { t, i18n } = useTranslation()
  const titleId = useId()
  const announcement = useAnnouncement(items)
  if (!items.length) return null
  const active = items.filter((item) => ACTIVE_STATES.includes(item.state)).length
  const done = items.filter((item) => item.state === 'done').length
  const finished = items.length - active - items.filter((item) => item.state === 'failed').length

  return (
    <section
      aria-labelledby={titleId}
      data-testid="upload-queue"
      className="flex flex-col gap-3 rounded-card border-[1.5px] border-ink bg-surface p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={titleId} className="flex items-center gap-2 text-base font-semibold">
          <FileUp className="size-4" aria-hidden="true" />
          {active
            ? t('library.upload.queueActive', { count: active })
            : t('library.upload.queueDone', { done, total: items.length })}
        </h2>
        {finished > 0 && (
          <Button variant="ghost" className="h-9 px-3 text-sm" onClick={onClear}>
            {t('library.upload.clear')}
          </Button>
        )}
      </div>
      <ul className="flex flex-col divide-y divide-line">
        {items.map((item) => (
          <UploadRow
            key={item.key}
            item={item}
            text={statusText(item, t, i18n.language)}
            onCancel={() => onCancel(item.key)}
            onRetry={() => onRetry(item.key)}
            onRename={onRename && (() => onRename(item))}
            onShowDuplicate={onShowDuplicate && (() => onShowDuplicate(item))}
          />
        ))}
      </ul>
      <p className="sr-only" aria-live="polite" data-testid="upload-announcement">
        {announcement}
      </p>
    </section>
  )
}

function UploadRow({
  item,
  text,
  onCancel,
  onRetry,
  onRename,
  onShowDuplicate,
}: {
  item: UploadItem
  text: string
  onCancel: () => void
  onRetry: () => void
  onRename?: () => void
  onShowDuplicate?: () => void
}) {
  const { t, i18n } = useTranslation()
  const labelId = useId()
  const percent = item.file.size > 0 ? Math.min(100, Math.round((item.sentBytes / item.file.size) * 100)) : 100
  const active = ACTIVE_STATES.includes(item.state)
  const problem = item.state === 'rejected' || item.state === 'failed'
  const name = item.document?.title ?? item.file.name

  return (
    <li className="flex flex-col gap-2 py-3 first:pt-1 last:pb-1" data-testid="upload-item" data-state={item.state}>
      <div className="flex items-start gap-3">
        <StateIcon item={item} />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <p id={labelId} className="truncate text-[0.9375rem] font-medium" title={name}>
            {item.state === 'done' && item.document ? (
              <Link to={documentPath(item.document.id)} className="underline-offset-4 hover:underline">
                {name}
              </Link>
            ) : (
              name
            )}
          </p>
          <p
            className={cn(
              'text-[0.8125rem] leading-snug [overflow-wrap:anywhere]',
              problem ? 'text-danger' : item.state === 'offline' ? 'text-warning' : 'text-muted',
            )}
            data-testid="upload-status"
          >
            {text}
            <span className="tabular-nums">
              {' · '}
              {formatBytes(item.file.size, i18n.language)}
            </span>
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          {active && (
            <Button variant="ghost" className="h-9 px-3 text-sm" onClick={onCancel} aria-label={t('library.upload.cancelFile', { name })}>
              {t('common.cancel')}
            </Button>
          )}
          {item.state === 'failed' && (
            <Button
              variant="outline"
              className="h-9 px-3 text-sm"
              onClick={onRetry}
              icon={<RotateCcw className="size-4" aria-hidden="true" />}
              aria-label={t('library.upload.retryFile', { name })}
            >
              {t('common.retry')}
            </Button>
          )}
          {item.state === 'done' && item.document && onRename && (
            <Button
              variant="ghost"
              className="h-9 px-3 text-sm"
              onClick={onRename}
              icon={<Pencil className="size-4" aria-hidden="true" />}
              aria-label={t('library.upload.renameFile', { name })}
            >
              {t('library.upload.rename')}
            </Button>
          )}
          {item.state === 'duplicate' && item.duplicate && (
            <>
              {onShowDuplicate && (
                <Button variant="ghost" className="hidden h-9 px-3 text-sm sm:inline-flex" onClick={onShowDuplicate}>
                  {t('library.upload.duplicate.details')}
                </Button>
              )}
              <Link to={documentPath(item.duplicate.documentId)} className={buttonClasses('outline', 'md', 'h-9 px-3 text-sm')}>
                {t('library.upload.duplicate.openExisting')}
              </Link>
            </>
          )}
        </div>
      </div>
      {(active || item.state === 'failed') && (
        <div
          role="progressbar"
          aria-labelledby={labelId}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          aria-valuetext={`${percent}%`}
          className="ml-8 h-2 overflow-hidden rounded-full border-[1.5px] border-ink bg-field"
        >
          <div
            className={cn(
              'h-full rounded-full transition-[width] duration-200',
              item.state === 'offline' || item.state === 'failed' ? 'bg-warning' : 'bg-ink',
            )}
            style={{ width: `${Math.max(percent, item.sentBytes > 0 ? 2 : 0)}%` }}
          />
        </div>
      )}
    </li>
  )
}

function StateIcon({ item }: { item: UploadItem }) {
  const className = 'mt-0.5 size-5 shrink-0'
  switch (item.state) {
    case 'done':
      return <CheckCircle2 className={cn(className, 'text-success')} aria-hidden="true" />
    case 'rejected':
    case 'failed':
      return <AlertTriangle className={cn(className, 'text-danger')} aria-hidden="true" />
    case 'duplicate':
      return <Copy className={className} aria-hidden="true" />
    case 'offline':
      return <WifiOff className={cn(className, 'text-warning')} aria-hidden="true" />
    case 'cancelled':
      return <X className={cn(className, 'text-muted')} aria-hidden="true" />
    default:
      return <FileUp className={className} aria-hidden="true" />
  }
}

/** Câu thông báo cho trình đọc màn hình về tệp vừa kết thúc gần nhất (xong, bị từ chối, trùng, lỗi, mất mạng). */
function useAnnouncement(items: UploadItem[]) {
  const { t, i18n } = useTranslation()
  const latest = items.reduce<UploadItem | undefined>(
    (found, item) => ((item.noticeSeq ?? 0) > (found?.noticeSeq ?? 0) ? item : found),
    undefined,
  )
  if (!latest) return ''
  const name = latest.document?.title ?? latest.file.name
  switch (latest.state) {
    case 'done':
      return t('library.upload.announce.done', { name, folder: latest.folderName })
    case 'duplicate':
      return t('library.upload.announce.duplicate', { name })
    case 'rejected':
    case 'failed':
      return t('library.upload.announce.problem', { name, reason: uploadProblem(latest, t, i18n.language) })
    case 'offline':
      return t('library.upload.announce.offline')
    default:
      return ''
  }
}
