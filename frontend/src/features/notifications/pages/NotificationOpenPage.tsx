import { BellOff } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { buttonClasses, ErrorState, Skeleton } from '@/shared/ui'
import { useOpenNotification } from '../api/queries'
import type { OpenedNotification } from '../types'

/**
 * Mở một thông báo bằng đường dẫn trực tiếp (/notifications/{mã}): đánh dấu đã đọc rồi chuyển thẳng tới đối tượng,
 * hoặc tới trang nội dung không còn. Thông báo của người khác được trả lời như không tồn tại (NCL-02-CN-002, TC-04).
 */
export function NotificationOpenPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const open = useOpenNotification()
  const { mutate } = open
  const started = useRef<string | null>(null)

  useEffect(() => {
    if (started.current === id) return
    started.current = id
    mutate(id, {
      onSuccess: (opened) =>
        navigate(opened.available && opened.path ? opened.path : routes.contentGone, { replace: true }),
    })
  }, [id, mutate, navigate])

  const notFound = open.isError && toApiError(open.error).status === 404
  const title = notFound ? t('notifications.notFound.title') : t('notifications.opening')
  const heading = usePageHeading(title, open.status)
  const goTo = (opened: OpenedNotification) =>
    navigate(opened.available && opened.path ? opened.path : routes.contentGone, { replace: true })

  if (notFound) {
    return (
      <div
        className="mx-auto flex max-w-lg flex-col items-center gap-4 py-10 text-center"
        data-testid="notification-not-found"
      >
        <span
          aria-hidden="true"
          className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
        >
          <BellOff className="size-7" />
        </span>
        <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
          {t('notifications.notFound.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('notifications.notFound.body')}</p>
        <Link to={routes.home} className={buttonClasses('dark', 'lg')}>
          {t('contentGone.home')}
        </Link>
      </div>
    )
  }
  if (open.isError) {
    return (
      <ErrorState
        title={t('notifications.openFailed')}
        requestId={toApiError(open.error).requestId}
        onRetry={() => mutate(id, { onSuccess: goTo })}
        retrying={open.isPending}
      />
    )
  }
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <h1 ref={heading} tabIndex={-1} className="sr-only">
        {t('notifications.opening')}
      </h1>
      <Skeleton className="h-9 w-2/3 max-w-md" />
      <Skeleton className="h-40" />
    </div>
  )
}
