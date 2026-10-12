import { Mail, UserRound } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { ProviderLogo } from '@/features/auth/components/OAuthButtons'
import { http } from '@/shared/api/httpClient'
import { Alert, Badge, Button, Checkbox, TextField } from '@/shared/ui'

/**
 * Chỉ có ở môi trường phát triển: trang cho phép quyền của nhà cung cấp định danh giả lập, để thử trọn luồng
 * đăng nhập bằng Google mà không cần tài khoản Google thật. Liệt kê đúng các quyền ứng dụng xin.
 */
export function DevOAuthAuthorizePage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [verified, setVerified] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const scopes = (params.get('scope') ?? '').split(' ').filter(Boolean)

  const respond = async (allow: boolean) => {
    if (allow && !/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError(t('validation.emailInvalid'))
      return
    }
    setBusy(true)
    try {
      const { data } = await http.post<{ redirectUrl: string }>('/dev/oauth/authorize', {
        redirectUri: params.get('redirect_uri'),
        state: params.get('state'),
        codeChallenge: params.get('code_challenge'),
        email: email.trim(),
        name: name.trim(),
        emailVerified: verified,
        allow,
      })
      window.location.assign(data.redirectUrl)
    } catch {
      setBusy(false)
      setError(t('errors.INTERNAL_ERROR'))
    }
  }

  return (
    <main className="grid min-h-dvh place-items-center bg-paper p-4">
      <div className="flex w-full max-w-md flex-col gap-5 rounded-card border-[1.5px] border-ink bg-surface p-6 shadow-pop">
        <div className="flex items-center justify-between gap-3">
          <span className="flex items-center gap-2 font-medium">
            <ProviderLogo id="google" className="size-6" />
            {t('devOAuth.provider')}
          </span>
          <Badge tone="sun">{t('devOAuth.badge')}</Badge>
        </div>
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold">{t('devOAuth.title')}</h1>
          <p className="text-sm text-muted">{t('devOAuth.body')}</p>
        </div>
        <section className="rounded-field bg-paper p-4 text-sm" aria-labelledby="dev-oauth-scopes">
          <h2 id="dev-oauth-scopes" className="font-medium">
            {t('devOAuth.permissions')}
          </h2>
          <ul className="mt-2 flex flex-col gap-1.5" data-testid="dev-oauth-scopes">
            {scopes.includes('profile') && (
              <li className="flex items-center gap-2">
                <UserRound className="size-4" aria-hidden="true" />
                {t('devOAuth.scopeProfile')}
              </li>
            )}
            {scopes.includes('email') && (
              <li className="flex items-center gap-2">
                <Mail className="size-4" aria-hidden="true" />
                {t('devOAuth.scopeEmail')}
              </li>
            )}
          </ul>
        </section>
        {error && <Alert tone="danger">{error}</Alert>}
        <fieldset disabled={busy} className="flex flex-col gap-4">
          <TextField label={t('devOAuth.email')} type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <TextField label={t('devOAuth.name')} value={name} onChange={(event) => setName(event.target.value)} />
          <Checkbox label={t('devOAuth.verified')} checked={verified} onChange={(event) => setVerified(event.target.checked)} />
        </fieldset>
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="outline" disabled={busy} onClick={() => void respond(false)}>
            {t('devOAuth.deny')}
          </Button>
          <Button variant="dark" loading={busy} onClick={() => void respond(true)}>
            {t('devOAuth.allow')}
          </Button>
        </div>
      </div>
    </main>
  )
}
