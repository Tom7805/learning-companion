import { CircleAlert, CircleCheck, Info } from 'lucide-react'
import { forwardRef, type HTMLAttributes } from 'react'
import { cn } from '@/shared/lib/cn'

type Tone = 'danger' | 'success' | 'info' | 'warning'

const tones: Record<Tone, string> = {
  danger: 'bg-danger-soft border-danger/40 text-ink',
  success: 'bg-success-soft border-success/40 text-ink',
  info: 'bg-sky-soft border-ink/15 text-ink',
  warning: 'bg-warning-soft border-warning/40 text-ink',
}

const icons = {
  danger: <CircleAlert className="size-5 shrink-0 text-danger" aria-hidden="true" />,
  success: <CircleCheck className="size-5 shrink-0 text-success" aria-hidden="true" />,
  info: <Info className="size-5 shrink-0 text-ink-soft" aria-hidden="true" />,
  warning: <CircleAlert className="size-5 shrink-0 text-warning" aria-hidden="true" />,
}

interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  tone?: Tone
}

/** Thông báo trong trang. Lỗi dùng role="alert" để trình đọc màn hình đọc ngay. */
export const Alert = forwardRef<HTMLDivElement, AlertProps>(function Alert(
  { tone = 'info', className, children, ...rest },
  ref,
) {
  return (
    <div
      ref={ref}
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-3 rounded-field border px-4 py-3 text-sm leading-relaxed animate-fade-up',
        tones[tone],
        className,
      )}
      {...rest}
    >
      {icons[tone]}
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  )
})
