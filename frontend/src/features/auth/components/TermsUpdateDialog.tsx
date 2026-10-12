import { FileText, ShieldCheck } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { routes } from '@/app/routes'
import { useAcceptTerms } from '@/features/privacy/api/queries'
import type { LegalDocument, PrivacyStatus } from '@/features/privacy/types'
import { toApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { Alert, Button } from '@/shared/ui'

interface TermsUpdateDialogProps {
  status: PrivacyStatus
  onLogout: () => void
  loggingOut?: boolean
}

/**
 * Điều khoản hoặc chính sách vừa lên phiên bản mới (NCL-01-CN-006, TC-03): nêu các điểm thay đổi và người học
 * phải đồng ý trước khi dùng tiếp. Không đóng được bằng Esc; chưa đồng ý thì có thể đăng xuất, dữ liệu giữ nguyên.
 */
export function TermsUpdateDialog({ status, onLogout, loggingOut }: TermsUpdateDialogProps) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const accept = useAcceptTerms(status.accountId)
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  const descriptionId = useId()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const element = dialog.current
    if (!element || element.open) return
    if (typeof element.showModal === 'function') element.showModal()
    else element.setAttribute('open', '')
    heading.current?.focus()
  }, [])

  const updated = [
    status.acceptedTermsVersion !== status.terms.version && {
      kind: 'terms' as const,
      document: status.terms,
      icon: FileText,
      to: routes.terms,
    },
    status.acceptedPrivacyVersion !== status.privacy.version && {
      kind: 'privacy' as const,
      document: status.privacy,
      icon: ShieldCheck,
      to: routes.privacy,
    },
  ].filter(Boolean) as { kind: 'terms' | 'privacy'; document: LegalDocument; icon: typeof FileText; to: string }[]

  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language, { dateStyle: 'long' }).format(new Date(iso))

  const agree = async () => {
    setError(null)
    try {
      await accept.mutateAsync({ terms: status.terms.version, privacy: status.privacy.version })
      // Các truy vấn bị chặn trong lúc chờ đồng ý được tải lại.
      void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'privacy' })
    } catch (caught) {
      const code = toApiError(caught).code
      if (code === 'TERMS_VERSION_OUTDATED') {
        // Vừa có bản mới hơn trong lúc đọc: tải lại để người học đọc đúng bản.
        void queryClient.invalidateQueries({ queryKey: queryKeys.privacyStatus(status.accountId) })
      }
      setError(t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    }
  }

  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      data-testid="terms-update-dialog"
      onCancel={(event) => event.preventDefault()}
      className="m-auto max-h-[92dvh] w-[min(94vw,560px)] overflow-y-auto rounded-card border-[1.5px] border-ink bg-surface p-0 text-ink shadow-pop backdrop:bg-ink/60"
    >
      <div className="flex flex-col gap-5 p-6 sm:p-7">
        <div className="flex flex-col gap-2">
          <h2 ref={heading} id={titleId} tabIndex={-1} className="text-2xl font-semibold leading-snug outline-none">
            {t('termsUpdate.title')}
          </h2>
          <p id={descriptionId} className="leading-relaxed text-ink-soft">
            {t('termsUpdate.body')}
          </p>
        </div>

        {updated.map(({ kind, document, icon: Icon, to }) => (
          <section
            key={kind}
            className="flex flex-col gap-2 rounded-card border-[1.5px] border-ink bg-sun-soft p-4"
            data-testid={`terms-update-${kind}`}
          >
            <h3 className="flex items-center gap-2 font-medium">
              <Icon className="size-4" aria-hidden="true" />
              {t(kind === 'terms' ? 'legal.terms' : 'legal.privacy')}
            </h3>
            <p className="text-[13px] text-ink-soft">
              {t('legal.version', { version: document.version, date: date(document.effectiveAt) })}
            </p>
            <p className="text-sm font-medium">{t('termsUpdate.changes')}</p>
            <ul className="flex list-disc flex-col gap-1 pl-5 text-sm leading-relaxed">
              {(document.changes?.length ? document.changes : [document.summary]).map((change) => (
                <li key={change}>{change}</li>
              ))}
            </ul>
            <a href={to} target="_blank" rel="noopener noreferrer" className="self-start text-sm font-medium underline underline-offset-4">
              {t('privacy.readFull')}
              <span className="sr-only"> {t('register.opensInNewTab')}</span>
            </a>
          </section>
        ))}

        <p className="text-sm text-muted">{t('termsUpdate.dataKept')}</p>
        {error && <Alert tone="danger">{error}</Alert>}

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onLogout} loading={loggingOut} disabled={accept.isPending}>
            {t('home.logout')}
          </Button>
          <Button variant="dark" onClick={() => void agree()} loading={accept.isPending} data-testid="accept-terms">
            {t('termsUpdate.accept')}
          </Button>
        </div>
      </div>
    </dialog>
  )
}
