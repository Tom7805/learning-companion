import { Copy, ExternalLink, Smartphone } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Skeleton, toast } from '@/shared/ui'
import { groupSecret } from '../lib/totp'
import type { TotpSetup } from '../types'

interface TotpEnrollmentProps {
  setup?: TotpSetup
  email: string
}

/**
 * Hướng dẫn thêm tài khoản quản trị vào ứng dụng sinh mã (NCL-03-CN-002): nhập khóa hoặc mở địa chỉ otpauth:// trên
 * chính điện thoại. Ô nhập mã đầu tiên do trang chứa đặt ngay bên dưới.
 */
export function TotpEnrollment({ setup, email }: TotpEnrollmentProps) {
  const { t } = useTranslation()
  const copy = async () => {
    if (!setup) return
    try {
      await navigator.clipboard.writeText(setup.secret)
      toast.success(t('adminLogin.enroll.copied'))
    } catch {
      toast.error(t('adminLogin.codes.copyFailed'))
    }
  }

  return (
    <ol className="flex flex-col gap-4" aria-label={t('adminLogin.enroll.stepsLabel')}>
      <Step number={1} title={t('adminLogin.enroll.step1Title')}>
        <p>{t('adminLogin.enroll.step1Body')}</p>
      </Step>
      <Step number={2} title={t('adminLogin.enroll.step2Title')}>
        <p>{t('adminLogin.enroll.step2Body', { email })}</p>
        {setup ? (
          <div className="mt-3 flex flex-col gap-3">
            <div className="flex items-center gap-2 rounded-field border-[1.5px] border-ink bg-paper py-2 pr-2 pl-4">
              <code className="min-w-0 flex-1 font-mono text-[0.9375rem] font-semibold tracking-wider break-words" data-testid="totp-secret">
                {groupSecret(setup.secret)}
              </code>
              <button
                type="button"
                onClick={() => void copy()}
                className="grid size-10 shrink-0 place-items-center rounded-lg text-ink-soft outline-none transition-colors hover:bg-ink/5 hover:text-ink focus-visible:ring-4 focus-visible:ring-focus/25"
                aria-label={t('adminLogin.enroll.copy')}
              >
                <Copy className="size-5" aria-hidden="true" />
              </button>
            </div>
            <a
              href={setup.otpauthUri}
              className="inline-flex items-center gap-2 self-start text-sm font-medium text-accent-ink underline underline-offset-4"
            >
              <Smartphone className="size-4" aria-hidden="true" />
              {t('adminLogin.enroll.openApp')}
              <ExternalLink className="size-3.5" aria-hidden="true" />
            </a>
          </div>
        ) : (
          <Skeleton className="mt-3 h-14" />
        )}
      </Step>
      <Step number={3} title={t('adminLogin.enroll.step3Title')}>
        <p>{t('adminLogin.enroll.step3Body')}</p>
      </Step>
    </ol>
  )
}

function Step({ number, title, children }: { number: number; title: string; children: ReactNode }) {
  return (
    <li className="flex gap-4">
      <span
        aria-hidden="true"
        className="grid size-8 shrink-0 place-items-center rounded-full border-[1.5px] border-ink bg-sun text-sm font-semibold"
      >
        {number}
      </span>
      <div className="flex min-w-0 flex-col gap-1 text-[0.9375rem] leading-relaxed text-ink-soft">
        <p className="font-semibold text-ink">{title}</p>
        {children}
      </div>
    </li>
  )
}
