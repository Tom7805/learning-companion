import { useQueryClient } from '@tanstack/react-query'
import { FileText, Globe } from 'lucide-react'
import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { queryKeys } from '@/shared/api/queryKeys'
import { cn } from '@/shared/lib/cn'
import { Dialog, toast } from '@/shared/ui'
import type { DocumentCard } from '../types'
import { PasteTextTab } from './PasteTextTab'
import { WebImportTab } from './WebImportTab'

export type AddSourceTab = 'web' | 'text'

const TABS: AddSourceTab[] = ['web', 'text']

/**
 * Thêm tài liệu vào thư mục đang mở mà không cần tệp (NCL-05-CN-004): dán đường dẫn trang web, hoặc dán văn bản kèm
 * tiêu đề. Trang không lấy được thì thẻ trang web gợi ý chuyển sang dán văn bản (TC-04). Tài liệu mới hiện ngay trong
 * kho ở trạng thái chờ xử lý, như tệp tải lên.
 */
export function AddSourceDialog({
  open,
  folderId,
  folderName,
  initialTab = 'web',
  onClose,
}: {
  open: boolean
  folderId: string | null
  folderName: string
  initialTab?: AddSourceTab
  onClose: () => void
}) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [tab, setTab] = useState<AddSourceTab>(initialTab)
  const [opened, setOpened] = useState(open)
  // Chuyển thẻ bằng phím thì tiêu điểm ở lại trên thẻ; mở hộp thoại hay bấm "dán văn bản thay thế" thì vào ô nhập đầu.
  const [focusField, setFocusField] = useState(true)
  const baseId = useId()
  const tabRefs = useRef<Record<AddSourceTab, HTMLButtonElement | null>>({ web: null, text: null })

  // Mỗi lần mở lại bắt đầu ở thẻ được chọn.
  if (open !== opened) {
    setOpened(open)
    if (open) {
      setTab(initialTab)
      setFocusField(true)
    }
  }

  const added = (document: DocumentCard) => {
    void client.invalidateQueries({ queryKey: queryKeys.library })
    void client.invalidateQueries({ queryKey: queryKeys.quota })
    toast.success(t('library.addSource.added', { title: document.title, folder: folderName }))
    onClose()
  }

  const select = (next: AddSourceTab) => {
    setFocusField(false)
    setTab(next)
    tabRefs.current[next]?.focus()
  }

  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = TABS.indexOf(tab)
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      select(TABS[(index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length])
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      select(event.key === 'Home' ? TABS[0] : TABS[TABS.length - 1])
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t('library.addSource.title')}
      description={t('library.addSource.description', { folder: folderName })}
      className="w-[min(94vw,640px)]"
    >
      <div className="flex flex-col gap-4" data-testid="add-source-dialog">
        <div role="tablist" aria-label={t('library.addSource.tabs')} className="grid grid-cols-2 gap-1 rounded-field border-[1.5px] border-ink bg-field p-1">
          {TABS.map((name) => {
            const selected = tab === name
            const Icon = name === 'web' ? Globe : FileText
            return (
              <button
                key={name}
                ref={(element) => {
                  tabRefs.current[name] = element
                }}
                type="button"
                role="tab"
                id={`${baseId}-tab-${name}`}
                aria-selected={selected}
                aria-controls={`${baseId}-panel-${name}`}
                tabIndex={selected ? 0 : -1}
                onClick={() => {
                  setFocusField(false)
                  setTab(name)
                }}
                onKeyDown={onTabKey}
                data-testid={`add-source-tab-${name}`}
                className={cn(
                  'inline-flex h-10 items-center justify-center gap-2 rounded-[10px] text-[0.9375rem] font-medium',
                  'transition-colors duration-150',
                  selected ? 'border-[1.5px] border-ink bg-surface text-ink shadow-[2px_2px_0_var(--color-ink)]' : 'text-ink-soft hover:bg-ink/5',
                )}
              >
                <Icon className="size-4" aria-hidden="true" />
                {t(`library.addSource.tab.${name}`)}
              </button>
            )
          })}
        </div>
        <div role="tabpanel" id={`${baseId}-panel-${tab}`} aria-labelledby={`${baseId}-tab-${tab}`}>
          {tab === 'web' ? (
            <WebImportTab
              folderId={folderId}
              autoFocus={focusField}
              onAdded={added}
              onPasteInstead={() => {
                setFocusField(true)
                setTab('text')
              }}
            />
          ) : (
            <PasteTextTab folderId={folderId} autoFocus={focusField} onAdded={added} />
          )}
        </div>
      </div>
    </Dialog>
  )
}
