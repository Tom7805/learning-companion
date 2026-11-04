import { Check, Info, PanelRight, Star } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui'
import type { DocumentCard } from '../types'

export interface DocumentItemHandlers {
  selected: Set<string>
  onToggleSelect: (id: string) => void
  onToggleStar: (document: DocumentCard) => void
  onInfo: (document: DocumentCard) => void
  onPeek: (document: DocumentCard) => void
  /** Bắt đầu kéo một tài liệu: kéo cả nhóm nếu tài liệu đang nằm trong nhóm đã chọn. */
  onDragStart: (document: DocumentCard, data: DataTransfer) => void
}

/** Ô chọn của một tài liệu: vùng chạm 44 × 44, ô vuông viền mực như ô đánh dấu chung. */
export function SelectBox({ document, checked, onToggle }: { document: DocumentCard; checked: boolean; onToggle: () => void }) {
  const { t } = useTranslation()
  return (
    <label className="relative grid size-11 shrink-0 cursor-pointer place-items-center rounded-xl hover:bg-ink/10">
      <input
        type="checkbox"
        checked={checked}
        onChange={onToggle}
        aria-label={t('library.selection.select', { title: document.title })}
        data-testid="select-document"
        className={cn(
          'peer size-6 cursor-pointer appearance-none rounded-md border-[1.5px] border-ink bg-surface',
          'checked:bg-ink focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-focus',
        )}
      />
      <Check
        aria-hidden="true"
        strokeWidth={3}
        className="pointer-events-none absolute size-4 text-on-ink opacity-0 peer-checked:opacity-100"
      />
    </label>
  )
}

export function StarButton({ document, onToggle, className }: { document: DocumentCard; onToggle: () => void; className?: string }) {
  const { t } = useTranslation()
  const starred = Boolean(document.starred)
  return (
    <IconButton
      label={starred ? t('library.star.remove', { title: document.title }) : t('library.star.add', { title: document.title })}
      aria-pressed={starred}
      data-testid="star-document"
      icon={<Star className={cn('size-5', starred && 'fill-current')} />}
      onClick={onToggle}
      className={cn('text-ink hover:bg-ink/10', className)}
    />
  )
}

export function InfoButton({ document, onClick }: { document: DocumentCard; onClick: () => void }) {
  const { t } = useTranslation()
  return (
    <IconButton
      label={t('library.info.open', { title: document.title })}
      title={t('library.info.action')}
      data-testid="document-info-button"
      icon={<Info className="size-5" />}
      onClick={onClick}
      className="text-ink hover:bg-ink/10"
    />
  )
}

export function PeekButton({ document, onClick }: { document: DocumentCard; onClick: () => void }) {
  const { t } = useTranslation()
  return (
    <IconButton
      label={t('library.peek.open', { title: document.title })}
      title={t('library.peek.action')}
      data-testid="document-peek-button"
      icon={<PanelRight className="size-5" />}
      onClick={onClick}
      className="text-ink hover:bg-ink/10"
    />
  )
}
