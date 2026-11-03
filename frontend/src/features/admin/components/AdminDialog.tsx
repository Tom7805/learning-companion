import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

export const adminFieldClass =
  'h-12 w-full rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem] outline-none ' +
  'focus:border-focus focus:ring-4 focus:ring-focus/15 aria-invalid:border-danger disabled:bg-field disabled:text-muted'

/** Ô nhập có nhãn luôn hiện phía trên, dùng cho biểu mẫu nhiều trường của khu quản trị. */
export function AdminField({
  label,
  htmlFor,
  hint,
  className,
  children,
}: {
  label: string
  htmlFor: string
  hint?: ReactNode
  className?: string
  children: ReactNode
}) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      <label htmlFor={htmlFor} className="text-sm font-medium text-ink-soft">
        {label}
      </label>
      {children}
      {hint && <p className="text-[0.8125rem] leading-snug text-muted">{hint}</p>}
    </div>
  )
}

interface AdminDialogProps {
  open: boolean
  title: string
  description?: ReactNode
  onClose: () => void
  children: ReactNode
  testId?: string
}

/** Hộp thoại biểu mẫu dùng thẻ dialog gốc: khóa tiêu điểm bên trong, Esc để đóng, cuộn được trên điện thoại. */
export function AdminDialog({ open, title, description, onClose, children, testId }: AdminDialogProps) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      data-testid={testId}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className="m-auto max-h-[92dvh] w-[min(94vw,620px)] rounded-card border-[1.5px] border-ink bg-surface p-0 text-ink shadow-pop backdrop:bg-scrim/45"
    >
      {open && (
        <div className="flex flex-col gap-5 p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <h2 id={titleId} className="text-xl font-semibold leading-snug">
                {title}
              </h2>
              {description && <div className="text-sm leading-relaxed text-muted">{description}</div>}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label={t('common.close')}
              className="grid size-9 shrink-0 place-items-center rounded-full outline-none hover:bg-ink/5 focus-visible:ring-4 focus-visible:ring-focus/25"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  )
}
