import { Mail } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'
import { routes } from '@/app/routes'

/** Lối tắt tới hộp thư thử, chỉ hiện khi chạy "npm run dev". */
export function DevToolbar() {
  const { t } = useTranslation()
  const location = useLocation()
  if (!import.meta.env.DEV || location.pathname === routes.devMailbox) return null
  return (
    <Link
      to={routes.devMailbox}
      target="_blank"
      className="fixed bottom-4 right-4 z-40 hidden items-center gap-2 sm:inline-flex rounded-full border-[1.5px] border-ink bg-sun px-4 py-2 text-sm font-medium shadow-pop"
    >
      <Mail className="size-4" aria-hidden="true" />
      {t('dev.mailbox')}
    </Link>
  )
}
