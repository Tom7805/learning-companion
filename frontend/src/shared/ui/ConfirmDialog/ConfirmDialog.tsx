import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from '../Button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description: ReactNode
  confirmLabel: string
  cancelLabel: string
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Hộp thoại xác nhận dùng thẻ <dialog> gốc: tự khóa tiêu điểm bên trong, phím Esc để hủy.
 * Tiêu điểm đặt sẵn ở nút Hủy để thao tác nguy hiểm không xảy ra do lỡ nhấn Enter.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') dialog.showModal()
      else dialog.setAttribute('open', '')
      cancelRef.current?.focus()
    } else if (!open && dialog.open) {
      if (typeof dialog.close === 'function') dialog.close()
      else dialog.removeAttribute('open')
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      onCancel={(event) => {
        event.preventDefault()
        onCancel()
      }}
      className="m-auto w-[min(92vw,440px)] rounded-card border-[1.5px] border-ink bg-surface p-0 text-ink shadow-pop backdrop:bg-ink/45"
    >
      {open && (
        <div className="flex flex-col gap-5 p-6">
          <h2 id={titleId} className="text-xl font-semibold leading-snug">
            {title}
          </h2>
          <div id={descriptionId} className="text-[15px] leading-relaxed text-ink-soft">
            {description}
          </div>
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Button ref={cancelRef} variant="outline" onClick={onCancel}>
              {cancelLabel}
            </Button>
            <Button variant="accent" onClick={onConfirm} loading={loading}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      )}
    </dialog>
  )
}
