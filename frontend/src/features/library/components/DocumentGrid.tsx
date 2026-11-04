import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import { Badge } from '@/shared/ui'
import type { DocumentCard, Tag } from '../types'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import { InfoButton, PeekButton, SelectBox, StarButton, type DocumentItemHandlers } from './DocumentItemControls'
import { TagChip } from './TagChip'

const tones = ['bg-sun', 'bg-lilac', 'bg-sky'] as const

interface DocumentGridProps extends DocumentItemHandlers {
  documents: DocumentCard[]
  tags: Map<string, Tag>
}

/**
 * Thẻ tài liệu theo phong cách mẫu: nền pastel, viền mực, nhãn loại ở góc. Bấm thẻ để mở trang tài liệu; các nút ở
 * góc để chọn, gắn sao, xem thông tin hoặc xem bên cạnh mà vẫn ở lại kho (NCL-02-CN-001, NCL-05-CN-001). Kéo thẻ thả
 * vào một thư mục trên cây để chuyển.
 */
export function DocumentGrid({ documents, tags, ...handlers }: DocumentGridProps) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()

  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" data-testid="document-grid">
      {documents.map((document, index) => {
        const selected = handlers.selected.has(document.id)
        const documentTags = (document.tagIds ?? []).flatMap((id) => tags.get(id) ?? [])
        return (
          <li
            key={document.id}
            className="relative"
            data-testid="document-item"
            data-title={document.title}
            onDragStart={(event) => handlers.onDragStart(document, event.dataTransfer)}
          >
            <Link
              to={documentPath(document.id)}
              data-testid="document-card"
              className={cn(
                'flex h-full flex-col gap-3 rounded-card border-[1.5px] border-ink p-5 pt-16 transition-transform duration-150',
                'hover:-translate-y-0.5 hover:shadow-pop focus-visible:-translate-y-0.5',
                tones[index % tones.length],
                selected && 'ring-4 ring-ink ring-offset-2 ring-offset-paper',
              )}
            >
              <span className="flex flex-wrap items-center gap-2">
                <Badge tone="ink">{t(`library.type.${document.type}`)}</Badge>
                <DocumentStatusBadge status={document.status} />
              </span>
              <span className="text-lg font-medium leading-snug [overflow-wrap:anywhere]">{document.title}</span>
              {documentTags.length > 0 && (
                <span className="flex flex-wrap gap-1.5">
                  {documentTags.map((tag) => (
                    <TagChip key={tag.id} tag={tag} />
                  ))}
                </span>
              )}
              <span className="mt-auto flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[0.8125rem] text-ink-soft">
                <span>{formatBytes(document.sizeBytes, i18n.language)}</span>
                <time dateTime={document.createdAt}>{format.dateTime(document.createdAt)}</time>
              </span>
            </Link>
            <span className="absolute left-2 top-2">
              <SelectBox document={document} checked={selected} onToggle={() => handlers.onToggleSelect(document.id)} />
            </span>
            <span className="absolute right-2 top-2 flex items-center">
              <StarButton document={document} onToggle={() => handlers.onToggleStar(document)} />
              <InfoButton document={document} onClick={() => handlers.onInfo(document)} />
              <PeekButton document={document} onClick={() => handlers.onPeek(document)} />
            </span>
          </li>
        )
      })}
    </ul>
  )
}
