import { MessageSquarePlus, MessagesSquare } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { ChatPanel, useConversation, useConversations } from '@/features/assistant'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { cn } from '@/shared/lib/cn'
import { Button, Skeleton } from '@/shared/ui'
import type { AttachedDocument, Workspace } from '../types'

/**
 * Thẻ hỏi đáp của không gian học (NCL-07-CN-001): hỏi trợ lý trên mọi tài liệu đã gắn và sẵn sàng (bỏ chọn bớt được),
 * xem lại các hội thoại trước của không gian học. Hội thoại đang mở nằm trên địa chỉ trang ({@code ?conversation=}).
 */
export function WorkspaceAssistant({ workspace, documents }: { workspace: Workspace; documents: AttachedDocument[] }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const [params, setParams] = useSearchParams()
  const listId = useId()
  const conversationId = params.get('conversation') ?? undefined
  const conversations = useConversations(workspace.id)
  // Hội thoại vừa tạo ngay tại đây: địa chỉ đổi theo nhưng vùng hỏi đáp giữ nguyên câu trả lời đang hiện.
  const [session, setSession] = useState(() => ({
    key: conversationId ?? 'new',
    id: conversationId,
    created: false,
    moving: false,
    round: 0,
  }))
  if (session.moving && conversationId === session.id) {
    setSession({ ...session, moving: false })
  } else if (!session.moving && conversationId !== session.id) {
    setSession({
      key: conversationId ?? `new-${session.round + 1}`,
      id: conversationId,
      created: false,
      moving: false,
      round: session.round + 1,
    })
  }
  useEffect(() => {
    if (!session.moving || !session.id) return
    const id = session.id
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set('conversation', id)
        return next
      },
      { replace: true },
    )
  }, [session.id, session.moving, setParams])
  const loaded = useConversation(session.created ? undefined : session.id)

  const choose = (id?: string) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (id) next.set('conversation', id)
      else next.delete('conversation')
      return next
    })

  const scope = documents.map(({ document }) => ({
    id: document.id,
    title: document.title,
    type: document.type,
    status: document.status ?? 'READY',
  }))

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_16rem]" data-testid="workspace-assistant">
      <div className="flex min-w-0 flex-col">
        {session.id && !session.created && loaded.isPending ? (
          <Skeleton className="h-56" />
        ) : (
          <ChatPanel
            key={session.key}
            conversation={loaded.data}
            workspaceId={workspace.id}
            workspaceDocuments={scope}
            greeting={
              <div className="flex flex-col items-start gap-2 rounded-card border-[1.5px] border-dashed border-ink/50 p-5" data-testid="workspace-chat-greeting">
                <p className="text-lg font-medium">{t('assistant.workspace.greeting', { name: workspace.name })}</p>
                <p className="text-sm leading-relaxed text-muted">
                  {scope.length ? t('assistant.workspace.body') : t('assistant.workspace.noDocuments')}
                </p>
              </div>
            }
            onConversation={(id) => setSession((current) => ({ ...current, id, created: true, moving: true }))}
          />
        )}
      </div>
      <aside aria-labelledby={listId} className="flex flex-col gap-2">
        <div className="flex items-center justify-between gap-2">
          <h2 id={listId} className="text-sm font-semibold">
            {t('assistant.workspace.history')}
          </h2>
          <Button
            variant="ghost"
            className="h-9 px-3 text-sm"
            onClick={() => choose(undefined)}
            icon={<MessageSquarePlus className="size-4" aria-hidden="true" />}
            data-testid="workspace-new-chat"
          >
            {t('assistant.workspace.new')}
          </Button>
        </div>
        {conversations.data?.length === 0 && <p className="text-sm text-muted">{t('assistant.workspace.empty')}</p>}
        <ul className="flex flex-col gap-1" data-testid="workspace-conversations">
          {conversations.data?.map((conversation) => (
            <li key={conversation.id}>
              <button
                type="button"
                onClick={() => choose(conversation.id)}
                aria-current={conversation.id === conversationId || undefined}
                className={cn(
                  'flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-ink/5',
                  conversation.id === conversationId && 'bg-sun-soft font-medium',
                )}
              >
                <MessagesSquare className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                <span className="flex min-w-0 flex-col">
                  <span className="truncate">{conversation.title}</span>
                  <span className="text-xs font-normal text-muted">{format.dateTime(conversation.updatedAt)}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  )
}
