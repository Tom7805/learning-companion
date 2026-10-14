import { Bell, FileX2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { useNotificationPanel } from '@/features/notifications'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Button, buttonClasses } from '@/shared/ui'

/**
 * Đối tượng một thông báo trỏ tới đã bị xóa, hết hạn hoặc được thay bằng bản mới (NCL-02-CN-002, TC-03): nói rõ
 * chuyện gì xảy ra thay vì báo lỗi, và cho quay lại thông báo hoặc trang chính.
 */
export function ContentGonePage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('contentGone.title'))
  const notifications = useNotificationPanel()

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 py-10 text-center" data-testid="content-gone">
      <span
        aria-hidden="true"
        className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-lilac"
      >
        <FileX2 className="size-7" />
      </span>
      <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
        {t('contentGone.title')}
      </h1>
      <p className="leading-relaxed text-muted">{t('contentGone.body')}</p>
      <div className="flex flex-wrap justify-center gap-3">
        <Link to={routes.home} className={buttonClasses('dark', 'lg')}>
          {t('contentGone.home')}
        </Link>
        <Button size="lg" icon={<Bell className="size-4" aria-hidden="true" />} onClick={notifications.open}>
          {t('contentGone.notifications')}
        </Button>
      </div>
    </div>
  )
}
