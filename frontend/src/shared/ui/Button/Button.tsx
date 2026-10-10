import { ArrowRight } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode, Ref } from 'react'
import { cn } from '@/shared/lib/cn'
import { Spinner } from '../Spinner'

type Variant = 'accent' | 'dark' | 'outline' | 'ghost'
type Size = 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** React 19 truyền ref như prop thường, được chuyển thẳng xuống thẻ button. */
  ref?: Ref<HTMLButtonElement>
  variant?: Variant
  size?: Size
  loading?: boolean
  icon?: ReactNode
}

const variants: Record<Variant, string> = {
  accent: 'bg-accent text-ink border-ink hover:bg-accent-strong',
  dark: 'bg-ink text-white border-ink hover:bg-ink-soft',
  outline: 'bg-surface text-ink border-ink hover:bg-paper',
  ghost: 'bg-transparent text-ink border-transparent hover:bg-ink/5',
}

const sizes: Record<Size, string> = {
  md: 'h-11 px-5 text-[15px]',
  lg: 'h-13 px-6 text-base',
}

export const buttonClasses = (variant: Variant = 'outline', size: Size = 'md', className?: string) =>
  cn(
    'inline-flex items-center justify-center gap-2 rounded-field border-[1.5px] font-medium',
    'transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-55',
    variants[variant],
    sizes[size],
    className,
  )

export function Button({
  variant = 'outline',
  size = 'md',
  loading = false,
  icon,
  className,
  children,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClasses(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner className="size-4" /> : icon}
      {children}
    </button>
  )
}

interface CtaButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  hint?: ReactNode
  loading?: boolean
}

/** Nút hành động chính dạng thẻ tím nhạt với nút tròn vàng, theo mẫu "Save and Continue". */
export function CtaButton({ children, hint, loading, className, disabled, type = 'button', ...rest }: CtaButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'group flex w-full items-center justify-between gap-4 rounded-card border-[1.5px] border-ink',
        'bg-lilac px-6 py-4 text-left text-ink transition-colors duration-150',
        'hover:bg-lilac-strong disabled:cursor-not-allowed disabled:opacity-60',
        className,
      )}
      {...rest}
    >
      <span className="flex flex-col">
        <span className="text-lg font-semibold">{children}</span>
        {hint && <span className="text-sm text-ink-soft">{hint}</span>}
      </span>
      <span
        aria-hidden="true"
        className={cn(
          'grid size-12 shrink-0 place-items-center rounded-full border-[1.5px] border-ink bg-sun',
          'transition-transform duration-200 group-enabled:group-hover:translate-x-1',
        )}
      >
        {loading ? <Spinner className="size-5" /> : <ArrowRight className="size-5" strokeWidth={2.25} />}
      </span>
    </button>
  )
}
