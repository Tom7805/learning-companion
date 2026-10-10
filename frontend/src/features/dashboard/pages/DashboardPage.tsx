import { Bookmark, CircleCheckBig } from 'lucide-react'
import { Trans, useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { cn } from '@/shared/lib/cn'
import { Badge } from '@/shared/ui'

const cards = [
  { key: 'library', tone: 'bg-sun', tag: 'ink' },
  { key: 'flashcards', tone: 'bg-lilac', tag: 'sun' },
  { key: 'assistant', tone: 'bg-sky', tag: 'lilac' },
] as const

/** Trang chính tạm thời sau khi xác thực; các khối học tập thật thuộc các story sau. */
export function DashboardPage() {
  const { t, i18n } = useTranslation()
  const { account } = useAuth()
  const heading = usePageHeading(t('home.greeting', { name: account?.displayName ?? '' }))
  if (!account) return null

  const verifiedAt = account.emailVerifiedAt
    ? new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(
        new Date(account.emailVerifiedAt),
      )
    : null

  return (
    <div className="flex flex-col gap-8">
      <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none" data-testid="home-greeting">
        {t('home.greeting', { name: account.displayName })}
      </h1>

      <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.8fr)]">
        <div className="flex flex-col gap-4 rounded-card bg-night p-7 text-white">
          <p className="text-sm text-white/70">{t('home.account')}</p>
          <h2 className="flex items-center gap-3 text-2xl font-medium">
            <CircleCheckBig className="size-7 text-mint" aria-hidden="true" />
            {t('home.ready')}
          </h2>
          <p className="leading-relaxed text-white/80">
            <Trans
              i18nKey="home.readyBody"
              values={{ email: account.email }}
              components={{ strong: <strong className="font-semibold text-white" /> }}
            />
          </p>
          <div className="mt-auto flex flex-wrap items-center gap-2 text-sm">
            <Badge tone="sun">{t(`home.role.${account.role}`)}</Badge>
            {verifiedAt && <span className="text-white/60">{t('home.verifiedAt', { time: verifiedAt })}</span>}
          </div>
        </div>

        <ul className="grid gap-4 sm:grid-cols-3 lg:grid-cols-1">
          {cards.map((card) => (
            <li key={card.key} className={cn('rounded-card border-[1.5px] border-ink p-5', card.tone)}>
              <div className="flex items-start justify-between gap-2">
                <Badge tone={card.tag}>{t(`home.cards.${card.key}.tag`)}</Badge>
                <Bookmark className="size-5" aria-hidden="true" />
              </div>
              <p className="mt-3 text-lg font-medium leading-snug">{t(`home.cards.${card.key}.title`)}</p>
              <p className="mt-3 text-sm text-ink-soft">{t('home.next')}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
