import { useEffect, useRef, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

interface DrawerProps {
  open: boolean
  onClose: () => void
  /** Nhãn của ngăn kéo cho trình đọc màn hình. */
  label: string
  id?: string
  /** "bottom": ngăn trượt từ dưới lên trên điện thoại, cao vừa nội dung. */
  side?: 'left' | 'right' | 'bottom'
  className?: string
  children: ReactNode
}

/**
 * Ngăn kéo trượt từ cạnh màn hình, dùng thẻ <dialog> gốc: khóa tiêu điểm bên trong, phần còn lại của trang không
 * bấm được. Đóng bằng Esc hoặc chạm ra ngoài; khi đóng, tiêu điểm trở về nút đã mở nó.
 */
export function Drawer({ open, onClose, label, id, side = 'left', className, children }: DrawerProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const opener = useRef<HTMLElement | null>(null)

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
      opener.current?.focus()
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      id={id}
      aria-label={label}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      // Chạm vào lớp phủ (ngoài ngăn kéo) thì sự kiện rơi vào chính thẻ dialog.
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      className={cn(
        'fixed m-0 max-w-none border-0 p-0 text-ink backdrop:bg-scrim/50',
        side === 'bottom'
          ? 'inset-x-0 bottom-0 top-auto h-auto max-h-[85dvh] w-full rounded-t-card animate-fade-up'
          : 'inset-y-0 h-dvh max-h-none w-[min(86vw,320px)]',
        side === 'left' && 'left-0 right-auto animate-slide-in-left',
        side === 'right' && 'left-auto right-0 animate-slide-in-right',
        className,
      )}
    >
      {open && <div className={side === 'bottom' ? undefined : 'h-full'}>{children}</div>}
    </dialog>
  )
}
