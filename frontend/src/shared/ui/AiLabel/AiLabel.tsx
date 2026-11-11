import { Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

/**
 * Nhãn nhỏ "Do AI sinh" (NCL-04-CN-005, QTN-23) gắn trên mọi câu trả lời và học liệu do trí tuệ nhân tạo sinh ra.
 * Nội dung người học đã sửa tay vẫn giữ nhãn, chỉ ghi thêm "Đã chỉnh sửa". Các chức năng sinh học liệu về sau dùng lại
 * thành phần này.
 */
export function AiLabel({ edited = false, className }: { edited?: boolean; className?: string }) {
  const { t } = useTranslation()
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md border border-ink/25 bg-lilac-soft px-1.5 py-0.5 text-[0.6875rem] font-medium leading-4 text-ink',
        className,
      )}
      title={t('ai.label.hint')}
      data-testid="ai-label"
      data-edited={edited || undefined}
    >
      <Sparkles className="size-3" aria-hidden="true" />
      {t('ai.label.text')}
      {edited && <span className="text-ink-soft">· {t('ai.label.edited')}</span>}
      <span className="sr-only">. {t('ai.label.hint')}</span>
    </span>
  )
}
