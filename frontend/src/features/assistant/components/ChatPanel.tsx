import { Paperclip } from 'lucide-react'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useLibraryDocuments } from '@/features/library/api/queries'
import { useViewerPanel } from '@/features/library/hooks/useViewerPanel'
import type { LibraryQuery } from '@/features/library/types'
import { useChatStream } from '../hooks/useChatStream'
import type { Citation, Conversation, ScopeDocument } from '../types'
import { ChatComposer } from './ChatComposer'
import { DocumentPickerDialog } from './DocumentPickerDialog'
import { MessageList } from './MessageList'
import { SourceScopePicker } from './SourceScopePicker'

const ALL_DOCUMENTS: LibraryQuery = {
  scope: 'ALL',
  folderId: null,
  types: [],
  tagIds: [],
  statuses: [],
  sort: 'OPENED',
  direction: 'DESC',
}

/**
 * Vùng hỏi đáp dùng chung cho hội thoại riêng và không gian học (NCL-07-CN-001). Trong không gian học, phạm vi là mọi
 * tài liệu đã gắn và sẵn sàng; người học bỏ chọn bớt được. Ở hội thoại riêng, người học đính kèm tài liệu từ kho hoặc
 * tải tệp mới. Số trích dẫn mở đúng đoạn ở khung xem bên.
 */
export function ChatPanel({
  conversation,
  workspaceId,
  workspaceDocuments,
  greeting,
  onConversation,
}: {
  conversation?: Conversation
  workspaceId?: string
  /** Tài liệu đang gắn với không gian học; không có thì là hội thoại riêng. */
  workspaceDocuments?: ScopeDocument[]
  /** Lời chào khi hội thoại chưa có câu nào. */
  greeting?: ReactNode
  onConversation?: (id: string) => void
}) {
  const { t } = useTranslation()
  const inWorkspace = workspaceDocuments !== undefined
  const chat = useChatStream({ initial: conversation, workspaceId, onConversation })
  const [attached, setAttached] = useState<ScopeDocument[]>(() => (inWorkspace ? [] : (conversation?.scope ?? [])))
  const [excluded, setExcluded] = useState<Set<string>>(() => new Set())
  const [picking, setPicking] = useState(false)
  const [draft, setDraft] = useState<{ key: number; text: string } | undefined>()
  // Trạng thái xử lý mới nhất của tài liệu đính kèm (tệp vừa tải lên chuyển dần sang sẵn sàng).
  const library = useLibraryDocuments(ALL_DOCUMENTS)
  const statusOf = useMemo(() => new Map((library.data ?? []).map((document) => [document.id, document.status])), [library.data])

  const documents: ScopeDocument[] = useMemo(() => {
    const base = inWorkspace ? workspaceDocuments : attached
    return base.map((document) => ({ ...document, status: statusOf.get(document.id) ?? document.status }))
  }, [attached, inWorkspace, statusOf, workspaceDocuments])
  const selected = useMemo(
    () => new Set(documents.filter((document) => !excluded.has(document.id)).map((document) => document.id)),
    [documents, excluded],
  )
  const titles = useMemo(() => {
    const map = new Map(documents.map((document) => [document.id, document.title]))
    for (const message of chat.messages) {
      for (const citation of message.citations) {
        if (citation.documentId) map.set(citation.documentId, citation.documentTitle)
      }
    }
    return map
  }, [chat.messages, documents])
  const { openViewer } = useViewerPanel((id) => titles.get(id))

  const ask = (question: string) => {
    const ready = documents.filter((document) => document.status === 'READY' && selected.has(document.id))
    // Trong không gian học, không bỏ chọn gì thì để máy chủ dùng phạm vi mặc định (mọi tài liệu đã gắn).
    const documentIds = inWorkspace && excluded.size === 0 ? undefined : ready.map((document) => document.id)
    chat.ask(question, documentIds)
  }

  const toggle = (id: string) =>
    setExcluded((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const openCitation = useCallback(
    (citation: Citation) => {
      if (!citation.documentId) return
      openViewer(citation.documentId, {
        kind: citation.location.kind,
        number: citation.location.number,
        passage: citation.passage,
      })
    },
    [openViewer],
  )

  const addDocument = (document: { id: string; title: string }) => {
    if (!inWorkspace) {
      setAttached((current) =>
        current.some((item) => item.id === document.id)
          ? current
          : [...current, { id: document.id, title: document.title, type: 'PDF', status: statusOf.get(document.id) ?? 'READY' }],
      )
    }
    setExcluded((current) => {
      const next = new Set(current)
      next.delete(document.id)
      return next
    })
  }

  const empty = chat.messages.length === 0

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-5" data-testid="chat-panel">
      {empty && greeting}
      {!empty && (
        <MessageList
          messages={chat.messages}
          busy={chat.busy}
          onOpenCitation={openCitation}
          onSuggestion={(question) => (chat.busy ? setDraft({ key: Date.now(), text: question }) : ask(question))}
          onRegenerate={chat.regenerate}
          onChooseSources={inWorkspace ? undefined : () => setPicking(true)}
          onAddDocument={inWorkspace && !workspaceDocuments?.length ? undefined : addDocument}
        />
      )}
      <div className="sticky bottom-0 mt-auto flex flex-col gap-3 bg-paper/95 pb-2 pt-2 backdrop-blur-sm">
        <SourceScopePicker
          documents={documents}
          selected={selected}
          onToggle={toggle}
          onAttach={inWorkspace ? undefined : () => setPicking(true)}
          onRemove={inWorkspace ? undefined : (id) => setAttached((current) => current.filter((item) => item.id !== id))}
        />
        <ChatComposer
          busy={chat.busy}
          canStop={chat.canStop}
          autoFocus={empty}
          draft={draft}
          onSend={ask}
          onStop={chat.stop}
          toolbar={
            inWorkspace ? undefined : (
              <button
                type="button"
                onClick={() => setPicking(true)}
                aria-label={t('assistant.sources.attach')}
                title={t('assistant.sources.attach')}
                data-testid="composer-attach"
                className="grid size-11 place-items-center rounded-xl text-ink-soft hover:bg-ink/5 hover:text-ink"
              >
                <Paperclip className="size-5" aria-hidden="true" />
              </button>
            )
          }
        />
        <p className="text-center text-xs text-muted">{t('assistant.disclaimer')}</p>
      </div>
      {!inWorkspace && (
        <DocumentPickerDialog
          open={picking}
          selected={attached}
          onClose={() => setPicking(false)}
          onApply={(next) => {
            setAttached(next)
            setExcluded(new Set())
            setPicking(false)
          }}
          onAdd={(document) =>
            setAttached((current) => [...current.filter((item) => item.id !== document.id), document])
          }
        />
      )}
    </div>
  )
}
