import { cn } from '@/shared/lib/cn'

interface SpinnerProps {
  className?: string
  label?: string
}

export function Spinner({ className, label }: SpinnerProps) {
  return (
    <span role={label ? 'status' : undefined} className="inline-flex items-center">
      <svg
        className={cn('size-5 animate-spin', className)}
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
        <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
      </svg>
      {label && <span className="sr-only">{label}</span>}
    </span>
  )
}
