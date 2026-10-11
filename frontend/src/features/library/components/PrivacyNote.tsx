import { ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

/** Nhắc người học rằng dữ liệu học tập chỉ mình họ mở được (QTN-07, QTN-08). */
export function PrivacyNote({ className }: { className?: string }) {
  const { t } = useTranslation()
  return (
    <p
      data-testid="privacy-note"
      className={cn(
        'flex items-start gap-3 rounded-card border-[1.5px] border-ink bg-lilac-soft p-4 text-sm leading-relaxed',
        className,
      )}
    >
      <ShieldCheck className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
      <span>{t('library.privacy')}</span>
    </p>
  )
}
