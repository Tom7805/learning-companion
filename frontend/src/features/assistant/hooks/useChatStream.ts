import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { queryKeys } from '@/shared/api/queryKeys'
import { assistantApi } from '../api/assistantApi'
import type { ChatMessage, Conversation, StreamHandlers } from '../types'

let localIds = 0
const localId = (prefix: string) => `${prefix}-${++localIds}`

function pendingAnswer(): ChatMessage {
  return {
    id: localId('answer'),
    role: 'ASSISTANT',
    content: '',
    status: 'STREAMING',
    documentIds: [],
    suggestions: [],
    citations: [],
    createdAt: new Date().toISOString(),
  }
}

/**
 * Một hội thoại đang mở với trợ lý (NCL-07-CN-001): gửi câu hỏi, nhận câu trả lời hiện dần, dừng giữa chừng, trả lời
 * lại. Hội thoại đã lưu được nạp từ {@code initial}; câu hỏi đầu của hội thoại mới tạo hội thoại trên máy chủ và báo mã
 * qua {@code onConversation}.
 */
export function useChatStream({
  initial,
  workspaceId,
  onConversation,
}: {
  initial?: Conversation
  workspaceId?: string
  onConversation?: (id: string) => void
}) {
  const client = useQueryClient()
  const [messages, setMessages] = useState<ChatMessage[]>(initial?.messages ?? [])
  const [conversationId, setConversationId] = useState<string | undefined>(initial?.id)
  const [answerId, setAnswerId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const controller = useRef<AbortController | null>(null)
  const loadedId = useRef(initial?.id)

  // Hội thoại khác được nạp (chọn trong danh sách, mở đường dẫn): thay toàn bộ, trừ khi đang trả lời.
  useEffect(() => {
    if (!initial || busyRef.current) return
    if (loadedId.current === initial.id && messages.length >= initial.messages.length) return
    loadedId.current = initial.id
    setConversationId(initial.id)
    setMessages(initial.messages)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initial])

  useEffect(() => () => controller.current?.abort(), [])

  const patchAnswer = useCallback(
    (id: string, change: (message: ChatMessage) => ChatMessage) =>
      setMessages((current) => current.map((message) => (message.id === id ? change(message) : message))),
    [],
  )

  const run = useCallback(
    async (start: (handlers: StreamHandlers, signal: AbortSignal) => Promise<void>, pendingId: string) => {
      busyRef.current = true
      setBusy(true)
      const abort = new AbortController()
      controller.current = abort
      let currentId = pendingId
      const handlers: StreamHandlers = {
        onMeta: (meta) => {
          setConversationId(meta.conversationId)
          loadedId.current = meta.conversationId
          setAnswerId(meta.answerId)
          setMessages((current) =>
            current.map((message) => {
              if (message.id === pendingId) return { ...message, id: meta.answerId }
              if (message.role === 'USER' && message.id.startsWith('question-')) {
                return { ...message, id: meta.questionId, documentIds: meta.documentIds }
              }
              return message
            }),
          )
          currentId = meta.answerId
          onConversation?.(meta.conversationId)
        },
        onChunk: (text) => patchAnswer(currentId, (message) => ({ ...message, content: message.content + text })),
        onSafety: (outcome) => patchAnswer(currentId, (message) => ({ ...message, safety: outcome })),
        onDone: (done) =>
          patchAnswer(currentId, (message) => ({
            ...message,
            status: done.status,
            content: done.text,
            suggestions: done.suggestions,
            citations: done.citations,
            elsewhere: done.elsewhere,
          })),
        onError: (code, partial) =>
          patchAnswer(currentId, (message) => ({
            ...message,
            status: 'FAILED',
            errorCode: code,
            content: partial || message.content,
          })),
      }
      try {
        await start(handlers, abort.signal)
      } finally {
        busyRef.current = false
        setBusy(false)
        setAnswerId(null)
        controller.current = null
        void client.invalidateQueries({ queryKey: queryKeys.quota })
        void client.invalidateQueries({ queryKey: queryKeys.assistant })
      }
    },
    [client, onConversation, patchAnswer],
  )

  const ask = useCallback(
    (question: string, documentIds?: string[]) => {
      if (busyRef.current) return
      const answer = pendingAnswer()
      setMessages((current) => [
        ...current,
        {
          id: localId('question'),
          role: 'USER',
          content: question,
          status: 'SENT',
          documentIds: documentIds ?? [],
          suggestions: [],
          citations: [],
          createdAt: new Date().toISOString(),
        },
        answer,
      ])
      void run(
        (handlers, signal) =>
          assistantApi.ask({ conversationId, workspaceId: conversationId ? undefined : workspaceId, question, documentIds }, handlers, signal),
        answer.id,
      )
    },
    [conversationId, run, workspaceId],
  )

  /** Trả lời lại câu hỏi cuối (sau lỗi, hay khi người học muốn câu trả lời khác). */
  const regenerate = useCallback(() => {
    if (busyRef.current || !conversationId) return
    const answer = pendingAnswer()
    setMessages((current) => {
      const lastQuestion = current.map((message) => message.role).lastIndexOf('USER')
      return [...current.slice(0, lastQuestion + 1), answer]
    })
    void run((handlers, signal) => assistantApi.regenerate(conversationId, handlers, signal), answer.id)
  }, [conversationId, run])

  /** Dừng câu trả lời đang hiện: máy chủ không đưa thêm chữ và lưu phần đã hiện. */
  const stop = useCallback(() => {
    if (conversationId && answerId) void assistantApi.stop(conversationId, answerId).catch(() => undefined)
  }, [answerId, conversationId])

  return { messages, conversationId, busy, canStop: Boolean(answerId), ask, regenerate, stop }
}
