import { BarChart3, CircleAlert, Lock, Mail } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { Badge, Switch } from '@/shared/ui'
import { useSetConsent } from '../api/queries'
import type { OptionalPurpose, PrivacyStatus } from '../types'

const icons = { ANONYMOUS_USAGE_ANALYTICS: BarChart3, PRODUCT_NEWS_EMAIL: Mail } as const

/**
 * Bật tắt từng mục đích không bắt buộc (QTN-03): tắt sẵn, thay đổi lưu và có hiệu lực ngay.
 * Hai mục bắt buộc (điều khoản, chính sách) hiện để người học biết mình đã đồng ý bản nào.
 */
export function ConsentToggles({ status }: { status: PrivacyStatus }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const setConsent = useSetConsent(status.accountId)
  const [saving, setSaving] = useState<OptionalPurpose | null>(null)
  const [errors, setErrors] = useState<Partial<Record<OptionalPurpose, string>>>({})
  const [announcement, setAnnouncement] = useState('')

  const change = async (purpose: OptionalPurpose, granted: boolean) => {
    setSaving(purpose)
    setErrors((current) => ({ ...current, [purpose]: undefined }))
    try {
      await setConsent.mutateAsync({ purpose, granted })
      setAnnouncement(t(granted ? 'privacy.purpose.turnedOn' : 'privacy.purpose.turnedOff', {
        purpose: t(`privacy.purpose.${purpose}.title`),
      }))
    } catch (error) {
      const code = toApiError(error).code
      setErrors((current) => ({ ...current, [purpose]: t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') }) }))
    } finally {
      setSaving(null)
    }
  }

  return (
    <section
      aria-labelledby="consent-heading"
      className="flex flex-col gap-1 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
    >
      <h2 id="consent-heading" className="text-xl font-medium">
        {t('privacy.purposesTitle')}
      </h2>
      <p className="mb-3 text-sm leading-relaxed text-muted">{t('privacy.purposesBody')}</p>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      <ul className="divide-y divide-line">
        {status.purposes.map((consent) => (
          <PurposeRow
            key={consent.purpose}
            purpose={consent.purpose}
            granted={consent.granted}
            meta={
              consent.granted && consent.updatedAt
                ? t('privacy.purpose.onSince', {
                    time: format.dateTime(consent.updatedAt),
                    version: consent.documentVersion,
                  })
                : t('privacy.purpose.off')
            }
            busy={saving === consent.purpose}
            error={errors[consent.purpose]}
            onChange={(granted) => void change(consent.purpose, granted)}
          />
        ))}
        {(['terms', 'privacy'] as const).map((kind) => {
          const accepted = kind === 'terms' ? status.acceptedTermsVersion : status.acceptedPrivacyVersion
          return (
            <li key={kind} className="flex items-start gap-4 py-4" data-testid={`required-${kind}`}>
              <span
                aria-hidden="true"
                className="grid size-10 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-paper"
              >
                <Lock className="size-4" />
              </span>
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  {t(kind === 'terms' ? 'legal.terms' : 'legal.privacy')}
                  <Badge tone="outline">{t('privacy.required')}</Badge>
                </p>
                <p className="text-sm text-muted">
                  {accepted ? t('privacy.acceptedVersion', { version: accepted }) : t('privacy.notAccepted')}
                </p>
              </div>
              <Link
                to={kind === 'terms' ? routes.terms : routes.privacy}
                target="_blank"
                className="shrink-0 text-sm font-medium text-accent-ink underline-offset-4 hover:underline"
              >
                {t('privacy.read')}
                <span className="sr-only"> {t('register.opensInNewTab')}</span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function PurposeRow({
  purpose,
  granted,
  meta,
  busy,
  error,
  onChange,
}: {
  purpose: OptionalPurpose
  granted: boolean
  meta: string
  busy: boolean
  error?: string
  onChange: (granted: boolean) => void
}) {
  const { t } = useTranslation()
  const titleId = useId()
  const descriptionId = useId()
  const Icon = icons[purpose]
  return (
    <li className="flex items-start gap-4 py-4" data-testid={`purpose-${purpose}`}>
      <span
        aria-hidden="true"
        className="grid size-10 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-lilac"
      >
        <Icon className="size-4" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p id={titleId} className="font-medium">
          {t(`privacy.purpose.${purpose}.title`)}
        </p>
        <p id={descriptionId} className="text-sm leading-relaxed text-ink-soft">
          {t(`privacy.purpose.${purpose}.body`)}
        </p>
        <p className="text-[0.8125rem] text-muted" data-testid="purpose-meta">
          {meta}
        </p>
        {error && (
          <p role="alert" className="flex items-center gap-1.5 text-sm text-danger">
            <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
            {error}
          </p>
        )}
      </div>
      <Switch
        checked={granted}
        busy={busy}
        onChange={onChange}
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
      />
    </li>
  )
}
