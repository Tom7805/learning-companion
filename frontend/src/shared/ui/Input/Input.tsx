import { CircleAlert, CircleCheck } from 'lucide-react'
import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label: string
  hint?: ReactNode
  error?: string
  valid?: boolean
  validLabel?: string
  trailing?: ReactNode
}

/**
 * Ô nhập theo mẫu: khi trống là ô xám với nhãn nằm trong; khi gõ hoặc đã có giá trị, nhãn nổi lên
 * nằm trên viền và ô chuyển nền trắng viền đậm; hợp lệ thì hiện dấu tích xanh.
 */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, valid, validLabel, trailing, id, className, placeholder, ...rest },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId
  const hintId = hint ? `${inputId}-hint` : undefined
  const errorId = error ? `${inputId}-error` : undefined
  const showValid = valid && !error

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          placeholder={placeholder ?? ' '}
          aria-invalid={error ? true : undefined}
          aria-describedby={[errorId, hintId].filter(Boolean).join(' ') || undefined}
          className={cn(
            'peer h-14 w-full rounded-field border-[1.5px] px-4 pt-1 text-[15px] text-ink outline-none',
            'border-ink bg-surface transition-colors duration-150',
            'placeholder:text-transparent focus:placeholder:text-subtle',
            'placeholder-shown:border-line placeholder-shown:bg-field',
            'focus:border-focus focus:bg-surface focus:ring-4 focus:ring-focus/15',
            // Trình duyệt tự điền: coi như đã có giá trị, giữ nền trắng thay cho nền xanh mặc định của Chrome.
            'autofill:border-ink autofill:shadow-[inset_0_0_0_1000px_var(--color-surface)]',
            'autofill:[-webkit-text-fill-color:var(--color-ink)]',
            (trailing || showValid || error) && 'pr-12',
            trailing && (showValid || error) && 'pr-20',
            error && 'border-danger placeholder-shown:border-danger focus:border-danger focus:ring-danger/15',
          )}
          {...rest}
        />
        <label
          htmlFor={inputId}
          className={cn(
            'pointer-events-none absolute left-3 top-0 -translate-y-1/2 rounded px-1.5 text-xs font-medium',
            'bg-surface text-ink-soft transition-all duration-150',
            'peer-placeholder-shown:top-1/2 peer-placeholder-shown:bg-transparent',
            'peer-placeholder-shown:text-[15px] peer-placeholder-shown:font-normal peer-placeholder-shown:text-muted',
            'peer-autofill:top-0 peer-autofill:bg-surface peer-autofill:text-xs peer-autofill:font-medium',
            'peer-autofill:text-ink-soft',
            'peer-focus:top-0 peer-focus:bg-surface peer-focus:text-xs peer-focus:font-medium peer-focus:text-focus',
            error && 'text-danger peer-placeholder-shown:text-danger peer-focus:text-danger',
          )}
        >
          {label}
        </label>
        <div className="absolute inset-y-0 right-3 flex items-center gap-1.5">
          {trailing}
          {showValid && (
            <CircleCheck
              role="img"
              aria-label={validLabel}
              aria-hidden={validLabel ? undefined : true}
              className="size-6 animate-pop fill-success text-white"
              strokeWidth={2.5}
            />
          )}
          {error && <CircleAlert aria-hidden="true" className="size-5 text-danger" />}
        </div>
      </div>
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-sm text-danger">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="text-[13px] leading-snug text-muted">
          {hint}
        </p>
      )}
    </div>
  )
})
