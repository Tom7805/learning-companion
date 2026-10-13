import { CloudOff, RotateCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Button } from '../Button'

interface ErrorStateProps {
  title: string
  description?: ReactNode
  icon?: ReactNode
  /** Có thì hiện nút thử lại. */
  onRetry?: () => void
  retrying?: boolean
  /** Mã tra cứu từ máy chủ, để người học báo lỗi chính xác. */
  requestId?: string
  className?: string
}

/** Trạng thái lỗi khi không tải được một vùng nội dung: nói chuyện gì xảy ra và cho thử lại. */
export function ErrorState({ title, description, icon, onRetry, retrying, requestId, className }: ErrorStateProps) {
  const { t } = useTranslation()
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-col items-center gap-3 rounded-card border-[1.5px] border-danger/40 bg-danger-soft px-6 py-10',
        'text-center',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className="grid size-14 place-items-center rounded-2xl border-[1.5px] border-ink bg-surface [&_svg]:size-6"
      >
        {icon ?? <CloudOff className="text-danger" />}
      </span>
      <p className="text-lg font-medium">{title}</p>
      {description && <p className="max-w-sm text-sm leading-relaxed text-ink-soft">{description}</p>}
      {requestId && <p className="text-[0.8125rem] text-muted">{t('errors.requestId', { id: requestId })}</p>}
      {onRetry && (
        <Button
          variant="outline"
          className="mt-1"
          loading={retrying}
          onClick={onRetry}
          icon={<RotateCw className="size-4" aria-hidden="true" />}
        >
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}
