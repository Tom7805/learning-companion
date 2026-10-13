import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

/** "Chào mừng tới Learning Companion" như dòng "Welcome to Learnify" của mẫu. */
export function Brand({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  const { t } = useTranslation()
  return (
    <p className={cn('flex items-baseline gap-2 text-sm', inverted ? 'text-white/70' : 'text-muted', className)}>
      {t('app.welcomeTo')}
      <span className="text-[1.375rem] font-bold tracking-tight text-accent-strong">{t('app.name')}</span>
    </p>
  )
}

/** Biểu tượng ứng dụng (trùng biểu tượng trên thẻ trình duyệt và màn hình chính). */
export function AppMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className={cn('size-9 shrink-0', className)}>
      <rect x="3" y="3" width="58" height="58" rx="16" fill="#ffd45c" stroke="#1d1d1f" strokeWidth="4" />
      <path d="M20 18v28h24" fill="none" stroke="#1d1d1f" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="44" cy="22" r="6" fill="#ff5a36" stroke="#1d1d1f" strokeWidth="3" />
    </svg>
  )
}
