import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { ChatMessage, Citation } from '../types'
import { AssistantMessage } from './AssistantMessage'

/**
 * Các tin nhắn của hội thoại theo thứ tự. Câu trả lời đang hiện dần được đọc cho trình đọc màn hình khi xong (vùng
 * thông báo nhẹ), không đọc từng chữ.
 */
export function MessageList({
  messages,
  busy,
  onOpenCitation,
  onSuggestion,
  onRegenerate,
  onChooseSources,
  onAddDocument,
}: {
  messages: ChatMessage[]
  busy: boolean
  onOpenCitation: (citation: Citation) => void
  onSuggestion: (question: string) => void
  onRegenerate: () => void
  onChooseSources?: () => void
  onAddDocument?: (document: { id: string; title: string }) => void
}) {
  const { t } = useTranslation()
  const end = useRef<HTMLDivElement>(null)
  const lastAnswer = [...messages].reverse().find((message) => message.role === 'ASSISTANT')
  const contentLength = lastAnswer?.content.length ?? 0

  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' })
  }, [messages.length, contentLength])

  return (
    <div className="flex flex-col gap-6" data-testid="message-list">
      {messages.map((message) =>
        message.role === 'USER' ? (
          <div key={message.id} className="flex justify-end" data-testid="user-message">
            <p className="max-w-[85%] whitespace-pre-wrap rounded-card rounded-br-md border-[1.5px] border-ink bg-sun-soft px-4 py-2.5 text-[0.9375rem] leading-relaxed [overflow-wrap:anywhere]">
              {message.content}
            </p>
          </div>
        ) : (
          <AssistantMessage
            key={message.id}
            message={message}
            last={message === lastAnswer}
            busy={busy}
            onOpenCitation={onOpenCitation}
            onSuggestion={onSuggestion}
            onRegenerate={onRegenerate}
            onChooseSources={onChooseSources}
            onAddDocument={onAddDocument}
          />
        ),
      )}
      <p className="sr-only" aria-live="polite" data-testid="answer-announcement">
        {lastAnswer && lastAnswer.status !== 'STREAMING' ? t(`assistant.announce.${lastAnswer.status}`, { defaultValue: '' }) : ''}
      </p>
      <div ref={end} />
    </div>
  )
}
