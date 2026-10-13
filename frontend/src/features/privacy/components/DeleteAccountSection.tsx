import { Trash2 } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { buttonClasses } from '@/shared/ui'

/** Lối vào xóa tài khoản trong mục Tài khoản của cài đặt (NCL-01-CN-009); việc xác nhận nằm ở trang riêng. */
export function DeleteAccountSection({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation()
  const titleId = useId()
  return (
    <section
      aria-labelledby={titleId}
      data-testid="delete-account"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-danger/50 bg-danger-soft p-5 sm:flex-row sm:items-center sm:p-6"
    >
      <span
        aria-hidden="true"
        className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-surface"
      >
        <Trash2 className="size-6 text-danger" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h3 id={titleId} className="text-lg font-semibold">
          {t('accountDeletion.section.title')}
        </h3>
        <p className="text-sm leading-relaxed text-ink-soft">{t('accountDeletion.section.body')}</p>
      </div>
      <Link to={routes.deleteAccount} onClick={onNavigate} className={buttonClasses('outline', 'md', 'shrink-0')}>
        {t('accountDeletion.section.action')}
      </Link>
    </section>
  )
}
