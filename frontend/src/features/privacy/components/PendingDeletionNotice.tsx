import { useQueryClient } from '@tanstack/react-query'
import { CircleCheck, Hourglass, LogOut } from 'lucide-react'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { routes } from '@/app/routes'
import { useLogout, type Account } from '@/features/auth'
import { toApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { reloadTo } from '@/shared/lib/navigation'
import { Alert, Brand, Button, CtaButton } from '@/shared/ui'
import { useCancelAccountDeletion } from '../api/queries'

const DAY_MS = 86_400_000

/**
 * Tài khoản đang chờ xóa đăng nhập lại (NCL-01-CN-009, TC-02): thay cho toàn bộ ứng dụng là câu hỏi có hủy yêu cầu
 * xóa không. Hủy thì tài khoản hoạt động lại với nguyên dữ liệu; không thì đăng xuất và giữ nguyên hạn xóa.
 */
export function PendingDeletionNotice({ account }: { account: Account }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const queryClient = useQueryClient()
  const cancel = useCancelAccountDeletion(account.id)
  const logout = useLogout(() => reloadTo(`${routes.login}?loggedOut=1`))
  const [restored, setRestored] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const heading = usePageHeading(
    restored ? t('accountDeletion.pending.restoredTitle') : t('accountDeletion.pending.title'),
    restored,
  )
  const [openedAt] = useState(() => Date.now())
  const deleteAt = account.deletionScheduledFor
  const daysLeft = deleteAt ? Math.max(0, Math.ceil((Date.parse(deleteAt) - openedAt) / DAY_MS)) : null

  const keep = async () => {
    setError(null)
    try {
      await cancel.mutateAsync()
      setRestored(true)
    } catch (caught) {
      setError(t(`errors.${toApiError(caught).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    }
  }

  return (
    <div className="min-h-dvh bg-paper p-0 sm:p-5 lg:p-8">
      <div className="mx-auto flex min-h-dvh max-w-[1240px] items-center justify-center bg-night p-0 sm:min-h-[calc(100dvh-2.5rem)] sm:rounded-[2rem] sm:p-3 lg:min-h-[calc(100dvh-4rem)]">
        <main
          id="main"
          className="flex w-full max-w-[640px] flex-col gap-7 bg-surface px-5 py-8 sm:rounded-[1.5rem] sm:px-10 sm:py-10"
          data-testid="pending-deletion"
        >
          <Brand />
          {restored ? (
            <>
              <span
                aria-hidden="true"
                className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-success-soft animate-pop"
              >
                <CircleCheck className="size-8 text-success" />
              </span>
              <header className="flex flex-col gap-3">
                <h1 ref={heading} tabIndex={-1} className="text-[1.75rem] leading-tight tracking-tight outline-none sm:text-[2rem]">
                  {t('accountDeletion.pending.restoredTitle')}
                </h1>
                <p className="text-[0.9375rem] leading-relaxed text-ink-soft">{t('accountDeletion.pending.restoredBody')}</p>
              </header>
              <CtaButton onClick={() => void queryClient.invalidateQueries({ queryKey: queryKeys.session })}>
                {t('accountDeletion.pending.continue')}
              </CtaButton>
            </>
          ) : (
            <>
              <span
                aria-hidden="true"
                className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun animate-pop"
              >
                <Hourglass className="size-8" />
              </span>
              <header className="flex flex-col gap-3">
                <h1 ref={heading} tabIndex={-1} className="text-[1.75rem] leading-tight tracking-tight outline-none sm:text-[2rem]">
                  {t('accountDeletion.pending.title')}
                </h1>
                {deleteAt && (
                  <p className="text-[0.9375rem] leading-relaxed text-ink-soft" data-testid="pending-deletion-date">
                    <Trans
                      i18nKey="accountDeletion.pending.body"
                      values={{ date: format.dateTime(deleteAt) }}
                      components={{ strong: <strong className="font-semibold text-ink" /> }}
                    />
                  </p>
                )}
                {daysLeft !== null && (
                  <p className="inline-flex self-start rounded-full border-[1.5px] border-ink bg-sun-soft px-3 py-1 text-sm font-medium">
                    {t('accountDeletion.pending.daysLeft', { count: daysLeft })}
                  </p>
                )}
              </header>
              {error && <Alert tone="danger">{error}</Alert>}
              <div className="flex flex-col gap-3">
                <p className="font-medium">{t('accountDeletion.pending.question')}</p>
                <CtaButton loading={cancel.isPending} hint={t('accountDeletion.pending.keepHint')} onClick={() => void keep()}>
                  {t('accountDeletion.pending.keep')}
                </CtaButton>
                <Button
                  variant="outline"
                  size="lg"
                  loading={logout.isPending}
                  onClick={() => logout.mutate()}
                  icon={<LogOut className="size-5" aria-hidden="true" />}
                >
                  {t('accountDeletion.pending.proceed')}
                </Button>
              </div>
              <p className="text-[0.8125rem] leading-snug text-muted">{t('accountDeletion.pending.locked')}</p>
            </>
          )}
        </main>
      </div>
    </div>
  )
}
