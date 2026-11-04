import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

interface DialogProps {
  open: boolean
  title: string
  description?: ReactNode
  onClose: () => void
  children: ReactNode
  /** Hàng nút ở đáy hộp thoại. */
  footer?: ReactNode
  className?: string
}

/**
 * Hộp thoại chung dùng thẻ <dialog> gốc như {@code ConfirmDialog}: khóa tiêu điểm bên trong, Esc để đóng, đóng xong
 * tiêu điểm trở về nút đã mở nó. Nội dung chỉ dựng khi mở để biểu mẫu luôn bắt đầu sạch.
 */
export function Dialog({ open, title, description, onClose, children, footer, className }: DialogProps) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDialogElement>(null)
  const opener = useRef<HTMLElement | null>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
      if (opener.current?.isConnected) opener.current.focus()
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      className={cn(
        'm-auto max-h-[min(90dvh,720px)] w-[min(92vw,480px)] rounded-card border-[1.5px] border-ink bg-surface p-0',
        'text-ink shadow-pop backdrop:bg-scrim/45',
        className,
      )}
    >
      {open && (
        <div className="flex flex-col gap-5 p-6">
          <div className="flex items-start justify-between gap-3">
            <div className="flex flex-col gap-1.5">
              <h2 id={titleId} className="text-xl font-semibold leading-snug">
                {title}
              </h2>
              {description && (
                <div id={descriptionId} className="text-[0.9375rem] leading-relaxed text-ink-soft">
                  {description}
                </div>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label={t('common.close')}
              className="-m-1.5 grid size-11 shrink-0 place-items-center rounded-xl text-muted hover:bg-ink/5 hover:text-ink"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </div>
          {children}
          {footer && <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">{footer}</div>}
        </div>
      )}
    </dialog>
  )
}
