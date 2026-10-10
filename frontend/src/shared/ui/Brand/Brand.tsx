import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

/** "Chào mừng tới Learning Companion" như dòng "Welcome to Learnify" của mẫu. */
export function Brand({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  const { t } = useTranslation()
  return (
    <p className={cn('flex items-baseline gap-2 text-sm', inverted ? 'text-white/70' : 'text-muted', className)}>
      {t('app.welcomeTo')}
      <span className="text-[22px] font-bold tracking-tight text-accent-strong">{t('app.name')}</span>
    </p>
  )
}
