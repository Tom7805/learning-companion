import { PanelRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { documentPath } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import { Badge, IconButton } from '@/shared/ui'
import type { DocumentCard } from '../types'
import { DocumentPeek } from './DocumentPeek'

const tones = ['bg-sun', 'bg-lilac', 'bg-sky'] as const

/**
 * Thẻ tài liệu theo phong cách mẫu: nền pastel, viền mực, nhãn loại ở góc. Bấm thẻ để mở trang tài liệu, hoặc nút
 * "Xem bên cạnh" để mở trong khung xem bên mà vẫn ở lại thư viện (NCL-02-CN-001).
 */
export function DocumentGrid({ documents }: { documents: DocumentCard[] }) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const panel = useSidePanel()

  const peek = (document: DocumentCard) =>
    panel.open({
      id: `document:${document.id}`,
      title: document.title,
      render: () => <DocumentPeek id={document.id} onOpenFull={panel.close} />,
    })

  return (
    <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" data-testid="document-grid">
      {documents.map((document, index) => (
        <li key={document.id} className="relative">
          <Link
            to={documentPath(document.id)}
            data-testid="document-card"
            className={cn(
              'flex h-full flex-col gap-3 rounded-card border-[1.5px] border-ink p-5 transition-transform duration-150',
              'hover:-translate-y-0.5 hover:shadow-pop focus-visible:-translate-y-0.5',
              tones[index % tones.length],
            )}
          >
            <span className="flex items-start justify-between gap-2 pr-10">
              <Badge tone="ink">{t(`library.type.${document.type}`)}</Badge>
            </span>
            <span className="text-lg font-medium leading-snug [overflow-wrap:anywhere]">{document.title}</span>
            <span className="mt-auto flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[0.8125rem] text-ink-soft">
              <span>{formatBytes(document.sizeBytes, i18n.language)}</span>
              <time dateTime={document.createdAt}>{format.dateTime(document.createdAt)}</time>
            </span>
          </Link>
          <IconButton
            label={t('library.peek.open', { title: document.title })}
            title={t('library.peek.action')}
            data-testid="document-peek-button"
            icon={<PanelRight className="size-5" />}
            onClick={() => peek(document)}
            className="absolute right-3 top-3 text-ink hover:bg-ink/10"
          />
        </li>
      ))}
    </ul>
  )
}
