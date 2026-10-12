import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

interface SwitchProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> {
  checked: boolean
  onChange: (checked: boolean) => void
  /** Đang lưu: giữ nguyên trạng thái hiển thị nhưng không cho bấm thêm. */
  busy?: boolean
}

/** Công tắc bật tắt theo mẫu: viền mực, nút tròn vàng khi bật. Trình đọc màn hình đọc là "công tắc". */
export const Switch = forwardRef<HTMLButtonElement, SwitchProps>(function Switch(
  { checked, onChange, busy, disabled, className, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-busy={busy || undefined}
      disabled={disabled || busy}
      onClick={() => onChange(!checked)}
      className={cn(
        'relative inline-flex h-8 w-14 shrink-0 items-center rounded-full border-[1.5px] border-ink transition-colors',
        'outline-none focus-visible:ring-4 focus-visible:ring-focus/30 disabled:cursor-not-allowed',
        checked ? 'bg-ink' : 'bg-field',
        busy && 'opacity-70',
        className,
      )}
      {...rest}
    >
      <span
        aria-hidden="true"
        className={cn(
          'inline-block size-6 rounded-full border-[1.5px] border-ink shadow-sm transition-transform',
          checked ? 'translate-x-[1.55rem] bg-sun' : 'translate-x-0.5 bg-surface',
        )}
      />
    </button>
  )
})
