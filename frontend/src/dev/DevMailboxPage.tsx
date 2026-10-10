import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, Inbox, RefreshCw, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { http } from '@/shared/api/httpClient'
import { cn } from '@/shared/lib/cn'
import { Badge, Button, Card } from '@/shared/ui'

interface CapturedMail {
  to: string
  subject: string
  template: string
  html: string
  links: string[]
  sentAt: string
}

interface ClockState {
  now: string
  offsetSeconds: number
}

const mailboxKey = ['dev', 'mailbox'] as const
const clockKey = ['dev', 'clock'] as const

/** Chỉ có ở môi trường phát triển: xem thư mô phỏng và tua đồng hồ máy chủ để thử các mốc hết hạn. */
export function DevMailboxPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const mailbox = useQuery({
    queryKey: mailboxKey,
    queryFn: async () => (await http.get<CapturedMail[]>('/dev/mailbox')).data,
    refetchInterval: 3000,
  })
  const clock = useQuery({ queryKey: clockKey, queryFn: async () => (await http.get<ClockState>('/dev/clock')).data })
  const [selected, setSelected] = useState(0)

  const clear = useMutation({
    mutationFn: () => http.delete('/dev/mailbox'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: mailboxKey }),
  })
  const shiftClock = useMutation({
    mutationFn: (seconds: number | null) =>
      seconds === null ? http.post('/dev/clock/reset') : http.post('/dev/clock/advance', { seconds }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clockKey }),
  })

  const mails = mailbox.data ?? []
  const current = mails[Math.min(selected, mails.length - 1)]
  const offsetHours = (clock.data?.offsetSeconds ?? 0) / 3600

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6 p-5 sm:p-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <Badge tone="sun">DEV</Badge>
          <h1 className="mt-2 text-3xl tracking-tight">{t('dev.mailboxTitle')}</h1>
          <p className="mt-1 text-muted">{t('dev.mailboxBody')}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={() => void mailbox.refetch()} icon={<RefreshCw className="size-4" aria-hidden="true" />}>
            {t('dev.refresh')}
          </Button>
          <Button onClick={() => clear.mutate()} icon={<Trash2 className="size-4" aria-hidden="true" />}>
            {t('dev.clear')}
          </Button>
        </div>
      </header>

      <Card className="flex flex-wrap items-center gap-3 p-4">
        <Clock className="size-5" aria-hidden="true" />
        <span className="font-medium">{t('dev.clock')}</span>
        <span className="text-sm text-muted" data-testid="clock-offset">
          {t('dev.clockOffset', { hours: offsetHours.toFixed(2) })}
        </span>
        <div className="ml-auto flex flex-wrap gap-2">
          <Button onClick={() => shiftClock.mutate(60)}>{t('dev.advance1m')}</Button>
          <Button onClick={() => shiftClock.mutate(25 * 3600)}>{t('dev.advance25h')}</Button>
          <Button variant="dark" onClick={() => shiftClock.mutate(null)}>
            {t('dev.resetClock')}
          </Button>
        </div>
      </Card>

      {mails.length === 0 ? (
        <Card className="grid place-items-center gap-3 p-12 text-muted">
          <Inbox className="size-8" aria-hidden="true" />
          {t('dev.empty')}
        </Card>
      ) : (
        <div className="grid gap-5 lg:grid-cols-[320px_minmax(0,1fr)]">
          <ul className="flex flex-col gap-2" aria-label={t('dev.mailbox')}>
            {mails.map((mail, index) => (
              <li key={`${mail.sentAt}-${index}`}>
                <button
                  type="button"
                  onClick={() => setSelected(index)}
                  aria-current={index === selected}
                  className={cn(
                    'w-full rounded-field border-[1.5px] border-ink p-3 text-left transition-colors',
                    index === selected ? 'bg-sun' : 'bg-surface hover:bg-paper',
                  )}
                >
                  <span className="block truncate text-sm font-medium">{mail.subject}</span>
                  <span className="block truncate text-xs text-muted">
                    {mail.to} ·{' '}
                    {new Intl.DateTimeFormat(i18n.language, { timeStyle: 'medium' }).format(new Date(mail.sentAt))}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {current && (
            <Card className="flex flex-col gap-4 p-4">
              <div className="text-sm">
                <span className="text-muted">{t('dev.to')}: </span>
                <strong>{current.to}</strong>
              </div>
              {current.links.length > 0 && (
                <div className="flex flex-col gap-2">
                  <span className="text-sm text-muted">{t('dev.links')}</span>
                  {current.links.map((link) => (
                    <a key={link} href={link} className="break-all text-sm text-accent-ink underline" data-testid="mail-link">
                      {link}
                    </a>
                  ))}
                </div>
              )}
              <iframe
                title={current.subject}
                sandbox="allow-popups allow-top-navigation-by-user-activation"
                srcDoc={current.html.replace('<head>', '<head><base target="_top">')}
                className="h-[560px] w-full rounded-field border border-line bg-paper"
              />
            </Card>
          )}
        </div>
      )}
    </div>
  )
}
