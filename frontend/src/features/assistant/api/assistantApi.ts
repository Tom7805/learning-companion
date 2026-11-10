import type { SafetyOutcome } from '@/features/safety'
import { http } from '@/shared/api/httpClient'
import { postEventStream } from '@/shared/api/sse'
import type { AskBody, Conversation, ConversationSummary, StreamDone, StreamHandlers, StreamMeta } from '../types'

const conversation = (id: string) => `/assistant/conversations/${encodeURIComponent(id)}`

function stream(url: string, body: unknown, handlers: StreamHandlers, signal?: AbortSignal) {
  return postEventStream(
    url,
    body,
    (name, data) => {
      if (name === 'meta') handlers.onMeta(data as unknown as StreamMeta)
      else if (name === 'chunk') handlers.onChunk(String(data.text ?? ''))
      else if (name === 'safety') handlers.onSafety(data as unknown as SafetyOutcome)
      else if (name === 'done') {
        handlers.onDone(data as unknown as StreamDone)
        return true
      } else if (name === 'error') {
        handlers.onError(String(data.code ?? 'INTERNAL_ERROR'), String(data.partial ?? ''), false)
        return true
      }
      return false
    },
    (code) => handlers.onError(code, '', true),
    signal,
  )
}

export const assistantApi = {
  /** Hỏi trợ lý; câu trả lời hiện dần qua các sự kiện. */
  ask(body: AskBody, handlers: StreamHandlers, signal?: AbortSignal) {
    return stream('/api/v1/assistant/ask', body, handlers, signal)
  },
  /** Trả lời lại câu hỏi cuối của hội thoại. */
  regenerate(conversationId: string, handlers: StreamHandlers, signal?: AbortSignal) {
    return stream(`/api/v1${conversation(conversationId)}/regenerate`, undefined, handlers, signal)
  },
  /** Dừng câu trả lời đang hiện; phần đã hiện được giữ. */
  async stop(conversationId: string, messageId: string) {
    await http.post(`${conversation(conversationId)}/messages/${encodeURIComponent(messageId)}/stop`)
  },
  async conversation(id: string) {
    const { data } = await http.get<Conversation>(conversation(id))
    return data
  },
  async conversations(workspaceId?: string) {
    const { data } = await http.get<ConversationSummary[]>('/assistant/conversations', {
      params: workspaceId ? { workspaceId } : undefined,
    })
    return data
  },
}
