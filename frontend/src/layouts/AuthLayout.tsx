import { Bookmark, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { cn } from '@/shared/lib/cn'
import { Badge, Brand } from '@/shared/ui'

/**
 * Khung trang đăng ký và xác thực theo "first look": viền tối bao ngoài như khung ứng dụng của mẫu,
 * bên trái là phần giới thiệu với các thẻ pastel, bên phải là vùng nội dung trắng bo góc lớn.
 */
export function AuthLayout() {
  const { t } = useTranslation()
  return (
    <div className="min-h-dvh bg-paper p-0 sm:p-5 lg:p-8">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-ink focus:px-4 focus:py-2 focus:text-white"
      >
        {t('app.skipToContent')}
      </a>
      <div className="mx-auto grid min-h-dvh max-w-[1240px] bg-night sm:min-h-[calc(100dvh-2.5rem)] sm:rounded-[2rem] sm:p-3 lg:min-h-[calc(100dvh-4rem)] lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
        <Showcase />
        <main
          id="main"
          className="flex flex-col bg-surface px-5 py-8 sm:rounded-[1.5rem] sm:px-10 sm:py-10 lg:px-14"
        >
          <Brand className="mb-8 lg:mb-10" />
          <div className="mx-auto flex w-full max-w-[440px] flex-1 flex-col justify-center animate-fade-up">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

const cards = [
  { key: 'library', tone: 'bg-sun', track: 'bg-[#e9bf3f]', fill: 0.66, rotate: '-rotate-2' },
  { key: 'flashcards', tone: 'bg-lilac', track: 'bg-[#a99ff0]', fill: 0.45, rotate: 'rotate-1' },
  { key: 'assistant', tone: 'bg-sky', track: 'bg-[#8ec8e8]', fill: 0.85, rotate: '-rotate-1' },
] as const

function Showcase() {
  const { t } = useTranslation()
  return (
    <aside className="hidden flex-col justify-between gap-10 px-8 py-10 text-white lg:flex xl:px-12">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-white/60">{t('app.name')}</p>
        <h2 className="max-w-md text-[34px] leading-[1.15] font-medium tracking-tight">{t('showcase.title')}</h2>
        <p className="max-w-md text-[15px] leading-relaxed text-white/70">{t('showcase.subtitle')}</p>
      </div>

      <ul className="flex flex-col gap-4" aria-label={t('showcase.title')}>
        {cards.map((card, index) => (
          <li
            key={card.key}
            className={cn(
              'rounded-card border-[1.5px] border-ink p-5 text-ink shadow-pop transition-transform duration-300 hover:rotate-0',
              card.tone,
              card.rotate,
              index === 1 && 'ml-8',
            )}
          >
            <div className="flex items-start justify-between gap-3">
              <Badge tone="ink">{t(`showcase.cards.${card.key}.tag`)}</Badge>
              <Bookmark className="size-5 fill-ink" aria-hidden="true" />
            </div>
            <p className="mt-3 text-lg font-medium leading-snug">{t(`showcase.cards.${card.key}.title`)}</p>
            <div className="mt-4 flex items-center justify-between text-xs">
              <span>{t('showcase.progress')}</span>
              <span>{t(`showcase.cards.${card.key}.value`)}</span>
            </div>
            <div className={cn('mt-1.5 h-2 overflow-hidden rounded-full', card.track)} aria-hidden="true">
              <div className="h-full rounded-full bg-ink" style={{ width: `${card.fill * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>

      <p className="flex items-start gap-3 text-sm leading-relaxed text-white/70">
        <ShieldCheck className="mt-0.5 size-5 shrink-0 text-mint" aria-hidden="true" />
        {t('showcase.privacy')}
      </p>
    </aside>
  )
}
