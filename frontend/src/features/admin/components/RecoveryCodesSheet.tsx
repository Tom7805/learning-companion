import { Copy, Download, KeyRound } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Button, Checkbox, CtaButton, toast } from '@/shared/ui'

interface RecoveryCodesSheetProps {
  codes: string[]
  email: string
  onDone: () => void
}

/**
 * Mười mã khôi phục, hiện đúng một lần ngay sau khi thiết lập ứng dụng sinh mã (NCL-03-CN-002). Phải xác nhận đã lưu
 * mới vào trang quản trị được, vì mất điện thoại mà không có mã thì không còn cách đăng nhập.
 */
export function RecoveryCodesSheet({ codes, email, onDone }: RecoveryCodesSheetProps) {
  const { t } = useTranslation()
  const [saved, setSaved] = useState(false)
  const listId = useId()
  const text = [t('adminLogin.codes.fileHeader', { email }), '', ...codes].join('\n')

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      toast.success(t('adminLogin.codes.copied'))
    } catch {
      toast.error(t('adminLogin.codes.copyFailed'))
    }
  }
  const download = () => {
    const url = URL.createObjectURL(new Blob([text + '\n'], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'learning-companion-ma-khoi-phuc.txt'
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="flex flex-col gap-6" data-testid="recovery-codes">
      <header className="flex flex-col gap-3">
        <span
          aria-hidden="true"
          className="grid size-14 place-items-center rounded-2xl border-[1.5px] border-ink bg-lilac"
        >
          <KeyRound className="size-6" />
        </span>
        <h1 className="text-[1.75rem] leading-tight font-normal tracking-tight">{t('adminLogin.codes.title')}</h1>
        <p className="text-[0.9375rem] leading-relaxed text-muted">{t('adminLogin.codes.body')}</p>
      </header>

      <Alert tone="warning">{t('adminLogin.codes.once')}</Alert>

      <ol
        id={listId}
        aria-label={t('adminLogin.codes.listLabel')}
        className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-card border-[1.5px] border-ink bg-paper p-5 font-mono text-[0.9375rem] tracking-wide"
      >
        {codes.map((code, index) => (
          <li key={code} className="flex items-baseline gap-2" data-testid="recovery-code">
            <span className="w-5 text-right text-xs text-muted" aria-hidden="true">
              {index + 1}.
            </span>
            <span>{code}</span>
          </li>
        ))}
      </ol>

      <div className="flex flex-wrap gap-3">
        <Button icon={<Copy className="size-4" aria-hidden="true" />} onClick={() => void copy()} aria-controls={listId}>
          {t('adminLogin.codes.copy')}
        </Button>
        <Button icon={<Download className="size-4" aria-hidden="true" />} onClick={download}>
          {t('adminLogin.codes.download')}
        </Button>
      </div>

      <Checkbox
        label={t('adminLogin.codes.confirm')}
        checked={saved}
        onChange={(event) => setSaved(event.currentTarget.checked)}
      />
      <CtaButton disabled={!saved} onClick={onDone} hint={t('adminLogin.codes.continueHint')}>
        {t('adminLogin.codes.continue')}
      </CtaButton>
    </div>
  )
}
