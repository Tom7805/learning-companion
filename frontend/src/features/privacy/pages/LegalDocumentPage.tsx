import { FileText, ShieldCheck } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, Spinner } from '@/shared/ui'
import { useLegalCurrent } from '../api/queries'

/** Trang điều khoản hoặc chính sách, hiển thị đúng phiên bản người học đồng ý khi đăng ký. */
export function LegalDocumentPage({ kind }: { kind: 'terms' | 'privacy' }) {
  const { t, i18n } = useTranslation()
  const legal = useLegalCurrent()
  const title = t(kind === 'terms' ? 'legal.terms' : 'legal.privacy')
  const heading = usePageHeading(title)
  const document = legal.data?.[kind]

  return (
    <article className="flex flex-col gap-6">
      <div
        aria-hidden="true"
        className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sky"
      >
        {kind === 'terms' ? <FileText className="size-7" /> : <ShieldCheck className="size-7" />}
      </div>
      <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
        {title}
      </h1>
      {legal.isPending && <Spinner label={t('common.loading')} />}
      {legal.isError && (
        <Alert tone="danger">
          <p>{t('legal.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void legal.refetch()}>
            {t('verify.failed.retry')}
          </Button>
        </Alert>
      )}
      {document && (
        <>
          <p className="text-sm text-muted" data-testid="legal-version">
            {t('legal.version', {
              version: document.version,
              date: new Intl.DateTimeFormat(i18n.language, { dateStyle: 'long' }).format(new Date(document.effectiveAt)),
            })}
          </p>
          <section className="rounded-card border-[1.5px] border-ink bg-sun-soft p-5">
            <h2 className="font-medium">{t('legal.summary')}</h2>
            <p className="mt-2 leading-relaxed text-ink-soft">{document.summary}</p>
          </section>
          <p className="text-sm text-muted">{t('legal.draftNote')}</p>
        </>
      )}
    </article>
  )
}
