import { FolderInput, Star, Tags, Trash2, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui'

interface BulkActionBarProps {
  count: number
  total: number
  busy?: boolean
  onSelectAll: () => void
  onClear: () => void
  onMove: () => void
  onTag: () => void
  onStar: () => void
  onDelete: () => void
}

/** Thanh thao tác cho nhiều tài liệu đã chọn: chuyển thư mục, gắn nhãn, gắn sao hoặc xóa cùng lúc. */
export function BulkActionBar({ count, total, busy, onSelectAll, onClear, onMove, onTag, onStar, onDelete }: BulkActionBarProps) {
  const { t } = useTranslation()
  return (
    <section
      aria-label={t('library.selection.bar')}
      data-testid="bulk-bar"
      className="sticky bottom-3 z-20 flex flex-wrap items-center gap-2 rounded-card border-[1.5px] border-ink bg-night p-3 text-on-night shadow-pop"
    >
      <p className="mr-auto px-2 text-sm font-medium" aria-live="polite">
        {t('library.selection.count', { count })}
      </p>
      {count < total && (
        <Button variant="ghost" className="text-on-night hover:bg-on-night/10" onClick={onSelectAll}>
          {t('library.selection.selectAll')}
        </Button>
      )}
      <Button variant="outline" onClick={onMove} disabled={busy} icon={<FolderInput className="size-4" aria-hidden="true" />}>
        {t('library.selection.move')}
      </Button>
      <Button variant="outline" onClick={onTag} disabled={busy} icon={<Tags className="size-4" aria-hidden="true" />}>
        {t('library.selection.tag')}
      </Button>
      <Button variant="outline" onClick={onStar} disabled={busy} icon={<Star className="size-4" aria-hidden="true" />}>
        {t('library.selection.star')}
      </Button>
      <Button variant="danger" onClick={onDelete} disabled={busy} icon={<Trash2 className="size-4" aria-hidden="true" />}>
        {t('library.selection.delete')}
      </Button>
      <button
        type="button"
        onClick={onClear}
        aria-label={t('library.selection.clear')}
        title={t('library.selection.clear')}
        className="grid size-11 place-items-center rounded-xl text-on-night/80 hover:bg-on-night/10 hover:text-on-night"
      >
        <X className="size-5" aria-hidden="true" />
      </button>
    </section>
  )
}
