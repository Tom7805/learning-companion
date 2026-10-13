import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { Alert, Spinner } from '@/shared/ui'
import { authApi } from '../api/authApi'
import { useOAuthProviders } from '../api/queries'

interface OAuthButtonsProps {
  /** Ghi nhớ thiết bị theo lựa chọn hiện tại ở biểu mẫu đăng nhập. */
  rememberDevice?: boolean
  /** Trang quay về sau khi đăng nhập xong. */
  returnTo?: string
  className?: string
}

/**
 * Nút đăng nhập qua nhà cung cấp định danh (NCL-01-CN-007). Chỉ hiện khi máy chủ bật nhà cung cấp; trình duyệt
 * được chuyển sang trang cho phép quyền của nhà cung cấp, chỉ xin quyền đọc tên và địa chỉ thư.
 */
export function OAuthButtons({ rememberDevice, returnTo, className }: OAuthButtonsProps) {
  const { t } = useTranslation()
  const providers = useOAuthProviders()
  const [starting, setStarting] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (!providers.data || providers.data.length === 0) return null

  const start = async (provider: string) => {
    setStarting(provider)
    setError(null)
    try {
      window.location.assign(await authApi.oauthStart(provider, { mode: 'LOGIN', rememberDevice, returnTo }))
    } catch (caught) {
      setStarting(null)
      const code = toApiError(caught).code
      setError(t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    }
  }

  return (
    <div className={cn('flex flex-col gap-3', className)}>
      <div className="flex items-center gap-3 text-sm text-muted" aria-hidden="true">
        <span className="h-px flex-1 bg-line" />
        {t('oauth.or')}
        <span className="h-px flex-1 bg-line" />
      </div>
      {providers.data.map((provider) => (
        <button
          key={provider.id}
          type="button"
          onClick={() => void start(provider.id)}
          disabled={starting !== null}
          aria-describedby="oauth-scope-hint"
          data-testid={`oauth-${provider.id}`}
          className="inline-flex h-12 items-center justify-center gap-3 rounded-field border-[1.5px] border-ink bg-surface px-5 font-medium transition-colors hover:bg-paper disabled:opacity-60 outline-none focus-visible:ring-4 focus-visible:ring-focus/30"
        >
          {starting === provider.id ? <Spinner className="size-5" /> : <ProviderLogo id={provider.id} />}
          {t('oauth.continueWith', { provider: provider.name })}
        </button>
      ))}
      <p id="oauth-scope-hint" className="text-center text-[0.8125rem] text-muted">
        {t('oauth.scopeHint')}
      </p>
      {error && <Alert tone="danger">{error}</Alert>}
    </div>
  )
}

/** Biểu tượng chữ G của Google theo hướng dẫn nhận diện cho nút đăng nhập. */
export function ProviderLogo({ id, className = 'size-5' }: { id: string; className?: string }) {
  if (id !== 'google') return null
  return (
    <svg viewBox="0 0 48 48" className={className} aria-hidden="true">
      <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
      <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
      <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
      <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
    </svg>
  )
}
