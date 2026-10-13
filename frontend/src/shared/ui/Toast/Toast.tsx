import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { useEffect, useSyncExternalStore } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { currentToasts, dismissToast, subscribeToasts, type ToastItem } from './toastStore'

const DURATION_MS = 5_000

const icons = {
  success: <CircleCheck className="size-5 shrink-0 text-success" aria-hidden="true" />,
  info: <Info className="size-5 shrink-0 text-ink-soft" aria-hidden="true" />,
  danger: <CircleAlert className="size-5 shrink-0 text-danger" aria-hidden="true" />,
}

function ToastCard({ item }: { item: ToastItem }) {
  const { t } = useTranslation()
  useEffect(() => {
    const timer = window.setTimeout(() => dismissToast(item.id), DURATION_MS)
    return () => window.clearTimeout(timer)
  }, [item.id])
  return (
    <li
      data-testid="toast"
      className={cn(
        'pointer-events-auto flex w-full items-start gap-3 rounded-field border-[1.5px] border-ink bg-surface',
        'px-4 py-3 text-sm leading-relaxed text-ink shadow-pop animate-fade-up',
      )}
    >
      {icons[item.tone]}
      <p className="min-w-0 flex-1">{item.message}</p>
      <button
        type="button"
        onClick={() => dismissToast(item.id)}
        aria-label={t('common.close')}
        className="-m-1 grid size-7 shrink-0 place-items-center rounded-lg text-muted hover:bg-ink/5 hover:text-ink"
      >
        <X className="size-4" aria-hidden="true" />
      </button>
    </li>
  )
}

/** Nơi hiện các thông báo nổi; đặt một lần ở gốc ứng dụng. */
export function Toaster() {
  const { t } = useTranslation()
  const list = useSyncExternalStore(subscribeToasts, currentToasts, currentToasts)
  return (
    // Vùng đọc to luôn có sẵn để trình đọc màn hình đọc từng thông báo ngay khi nó xuất hiện.
    <ol
      aria-live="polite"
      aria-relevant="additions"
      aria-label={t('common.notifications')}
      data-testid="toaster"
      className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex flex-col items-stretch gap-2 sm:left-auto sm:w-96"
    >
      {list.map((item) => (
        <ToastCard key={item.id} item={item} />
      ))}
    </ol>
  )
}
