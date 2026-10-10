import { Check } from 'lucide-react'
import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode
  error?: string
}

/** Ô đánh dấu vuông bo góc, viền mực, đánh dấu thì nền vàng. Dùng input thật nên bàn phím và trình đọc màn hình hoạt động sẵn. */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { label, error, id, className, ...rest },
  ref,
) {
  const autoId = useId()
  const inputId = id ?? autoId
  const errorId = error ? `${inputId}-error` : undefined

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <div className="flex items-start gap-3">
        <span className="relative mt-0.5 grid size-6 shrink-0 place-items-center">
          <input
            ref={ref}
            id={inputId}
            type="checkbox"
            aria-invalid={error ? true : undefined}
            aria-describedby={errorId}
            className={cn(
              'peer size-6 cursor-pointer appearance-none rounded-md border-[1.5px] border-ink bg-surface',
              'transition-colors checked:bg-sun focus-visible:outline-3 focus-visible:outline-offset-2',
              'focus-visible:outline-focus',
              error && 'border-danger',
            )}
            {...rest}
          />
          <Check
            aria-hidden="true"
            className="pointer-events-none absolute size-4 text-ink opacity-0 transition-opacity peer-checked:opacity-100"
            strokeWidth={3}
          />
        </span>
        <label htmlFor={inputId} className="cursor-pointer text-sm leading-relaxed text-ink-soft">
          {label}
        </label>
      </div>
      {error && (
        <p id={errorId} className="pl-9 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
})
