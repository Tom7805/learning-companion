import { HardDrive } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuotaStatus } from '@/features/quota'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'

/** Thanh dung lượng của kho: đã dùng bao nhiêu trên hạn mức chung (NCL-04-CN-003, NCL-05-CN-001). */
export function StorageMeter({ className }: { className?: string }) {
  const { t, i18n } = useTranslation()
  const labelId = useId()
  const quota = useQuotaStatus()
  const storage = quota.data?.storage
  if (!storage) return null
  const ratio = storage.limitBytes > 0 ? Math.min(1, storage.usedBytes / storage.limitBytes) : 0
  const percent = Math.round(ratio * 100)
  const nearlyFull = ratio >= 0.9
  const used = formatBytes(storage.usedBytes, i18n.language)
  const limit = formatBytes(storage.limitBytes, i18n.language)

  return (
    <div className={cn('flex flex-col gap-2 rounded-card border-[1.5px] border-ink bg-surface p-4', className)} data-testid="storage-meter">
      <div className="flex items-center gap-2 text-sm font-medium">
        <HardDrive className="size-4" aria-hidden="true" />
        <span id={labelId}>{t('library.storage.label')}</span>
      </div>
      <div
        role="meter"
        aria-labelledby={labelId}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        aria-valuetext={t('library.storage.used', { used, limit })}
        className="h-2.5 overflow-hidden rounded-full border-[1.5px] border-ink bg-field"
      >
        <div
          className={cn('h-full rounded-full', nearlyFull ? 'bg-danger' : 'bg-ink')}
          style={{ width: `${Math.max(percent, storage.usedBytes > 0 ? 2 : 0)}%` }}
        />
      </div>
      <p className="text-[0.8125rem] tabular-nums text-ink-soft">{t('library.storage.used', { used, limit })}</p>
      {nearlyFull && <p className="text-[0.8125rem] leading-snug text-danger">{t('library.storage.nearlyFull')}</p>}
    </div>
  )
}
