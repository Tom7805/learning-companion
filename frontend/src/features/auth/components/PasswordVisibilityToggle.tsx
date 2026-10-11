import { Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'

interface PasswordVisibilityToggleProps {
  shown: boolean
  onToggle: () => void
  /** Các ô mật khẩu cùng được hiện hoặc ẩn bởi nút này. */
  controls?: string
}

/** Nút con mắt đặt cuối ô mật khẩu. */
export function PasswordVisibilityToggle({ shown, onToggle, controls }: PasswordVisibilityToggleProps) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={shown}
      aria-controls={controls}
      aria-label={shown ? t('register.password.hide') : t('register.password.show')}
      className="grid size-9 place-items-center rounded-lg text-muted transition-colors hover:bg-ink/5 hover:text-ink"
    >
      {shown ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
    </button>
  )
}
