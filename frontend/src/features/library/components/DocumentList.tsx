import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import type { DocumentCard, Tag } from '../types'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import { InfoButton, PeekButton, SelectBox, StarButton, type DocumentItemHandlers } from './DocumentItemControls'
import { TagChip } from './TagChip'

interface DocumentListProps extends DocumentItemHandlers {
  documents: DocumentCard[]
  tags: Map<string, Tag>
}

/** Dạng danh sách gọn để lướt nhiều tài liệu: mỗi dòng có tên, loại, nhãn, dung lượng và ngày thêm. */
export function DocumentList({ documents, tags, ...handlers }: DocumentListProps) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  return (
    <ul
      className="flex flex-col divide-y-[1.5px] divide-line overflow-hidden rounded-card border-[1.5px] border-ink bg-surface"
      data-testid="document-list"
    >
      {documents.map((document) => {
        const selected = handlers.selected.has(document.id)
        const documentTags = (document.tagIds ?? []).flatMap((id) => tags.get(id) ?? [])
        return (
          <li
            key={document.id}
            data-testid="document-item"
            data-title={document.title}
            onDragStart={(event) => handlers.onDragStart(document, event.dataTransfer)}
            className={cn('flex items-center gap-1 px-2 py-1.5', selected && 'bg-sun-soft')}
          >
            <SelectBox document={document} checked={selected} onToggle={() => handlers.onToggleSelect(document.id)} />
            <Link
              to={documentPath(document.id)}
              data-testid="document-card"
              className="flex min-w-0 flex-1 flex-col gap-1 rounded-lg px-2 py-2 hover:bg-ink/5"
            >
              <span className="font-medium leading-snug [overflow-wrap:anywhere]">{document.title}</span>
              <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[0.8125rem] text-ink-soft">
                <span className="font-medium text-ink">{t(`library.type.${document.type}`)}</span>
                <span>{formatBytes(document.sizeBytes, i18n.language)}</span>
                <time dateTime={document.createdAt}>{format.dateTime(document.createdAt)}</time>
                <DocumentStatusBadge status={document.status} />
                {documentTags.map((tag) => (
                  <TagChip key={tag.id} tag={tag} />
                ))}
              </span>
            </Link>
            <StarButton document={document} onToggle={() => handlers.onToggleStar(document)} />
            <InfoButton document={document} onClick={() => handlers.onInfo(document)} />
            <PeekButton document={document} onClick={() => handlers.onPeek(document)} />
          </li>
        )
      })}
    </ul>
  )
}
