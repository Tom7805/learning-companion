import { Check, FileText, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import type { LegalDocument } from '../types'

/** Văn bản đang áp dụng: phiên bản, ngày hiệu lực và các điểm chính viết bằng lời dễ hiểu. */
export function TermsSummary({ terms, privacy }: { terms: LegalDocument; privacy: LegalDocument }) {
  const { t, i18n } = useTranslation()
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language, { dateStyle: 'long' }).format(new Date(iso))
  const documents = [
    { kind: 'terms' as const, document: terms, icon: FileText, to: routes.terms },
    { kind: 'privacy' as const, document: privacy, icon: ShieldCheck, to: routes.privacy },
  ]
  return (
    <section
      aria-labelledby="terms-summary-heading"
      className="flex flex-col gap-5 rounded-card border-[1.5px] border-ink bg-sun-soft p-5 sm:p-6"
    >
      <h2 id="terms-summary-heading" className="text-xl font-medium">
        {t('privacy.documentsTitle')}
      </h2>
      {documents.map(({ kind, document, icon: Icon, to }) => (
        <article key={kind} className="flex flex-col gap-2" data-testid={`summary-${kind}`}>
          <h3 className="flex items-center gap-2 font-medium">
            <Icon className="size-4" aria-hidden="true" />
            {t(kind === 'terms' ? 'legal.terms' : 'legal.privacy')}
          </h3>
          <p className="text-[13px] text-ink-soft">
            {t('legal.version', { version: document.version, date: date(document.effectiveAt) })}
          </p>
          <ul className="flex flex-col gap-1.5 text-sm leading-relaxed">
            {(document.highlights?.length ? document.highlights : [document.summary]).map((point) => (
              <li key={point} className="flex gap-2">
                <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                <span>{point}</span>
              </li>
            ))}
          </ul>
          <Link to={to} target="_blank" className="-my-2 self-start py-2 text-sm font-medium underline underline-offset-4">
            {t('privacy.readFull')}
            <span className="sr-only"> {t('register.opensInNewTab')}</span>
          </Link>
        </article>
      ))}
    </section>
  )
}
