import { isAxiosError } from 'axios'
import type { SafetyOutcome } from '@/features/safety'
import { http } from '@/shared/api/httpClient'
import { postEventStream } from '@/shared/api/sse'
import type { AiDisclosure, AnswerFile, AskBody, Citation, Conversation, ConversationSummary, StreamDone, StreamHandlers, StreamMeta } from '../types'

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
  /** Đoạn gốc của một trích dẫn, kèm việc tài liệu còn mở được không (NCL-07-CN-002). */
  async citation(id: string) {
    const { data } = await http.get<Citation>(`/assistant/citations/${encodeURIComponent(id)}`)
    return data
  },
  /** Đã đọc thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo chưa (NCL-04-CN-005). */
  async disclosure() {
    const { data } = await http.get<AiDisclosure>('/assistant/disclosure')
    return data
  },
  async acknowledgeDisclosure() {
    const { data } = await http.post<AiDisclosure>('/assistant/disclosure')
    return data
  },
  /**
   * Tải một câu trả lời về dạng Markdown, có dấu nhận biết do trí tuệ nhân tạo sinh ra trong siêu dữ liệu. Bộ đính dấu
   * lỗi thì máy chủ trả lỗi tạm thời (AI_LABEL_UNAVAILABLE) thay vì tệp; lỗi đọc từ Blob về JSON để giao diện báo đúng.
   */
  async exportAnswer(conversationId: string, messageId: string): Promise<AnswerFile> {
    return markdownFile(`${conversation(conversationId)}/messages/${encodeURIComponent(messageId)}/export`)
  },
  /** Tải cả hội thoại về dạng Markdown kèm nguồn của từng câu trả lời (NCL-07-CN-003). */
  async exportConversation(id: string): Promise<AnswerFile> {
    return markdownFile(`${conversation(id)}/export`)
  },
  /** Lịch sử hội thoại: đã ghim lên trước rồi mới dùng trước; {@code query} tìm theo tên và nội dung. */
  async conversations(workspaceId?: string, query?: string) {
    const params: Record<string, string> = {}
    if (workspaceId) params.workspaceId = workspaceId
    if (query?.trim()) params.q = query.trim()
    const { data } = await http.get<ConversationSummary[]>('/assistant/conversations', { params })
    return data
  },
  async updateConversation(id: string, change: { title?: string; pinned?: boolean }) {
    const { data } = await http.patch<ConversationSummary>(conversation(id), change)
    return data
  },
  /** Chuyển hội thoại vào không gian học; {@code null} thì thành hội thoại riêng. */
  async moveConversation(id: string, workspaceId: string | null) {
    const { data } = await http.put<ConversationSummary>(`${conversation(id)}/workspace`, { workspaceId })
    return data
  },
  async deleteConversation(id: string) {
    await http.delete(conversation(id))
  },
}

/**
 * Tệp Markdown có dấu nhận biết do trí tuệ nhân tạo sinh ra. Bộ đính dấu lỗi thì máy chủ trả lỗi tạm thời
 * (AI_LABEL_UNAVAILABLE) thay vì tệp; lỗi đọc từ Blob về JSON để giao diện báo đúng.
 */
async function markdownFile(url: string): Promise<AnswerFile> {
  try {
    const response = await http.get<Blob>(url, {
      responseType: 'blob',
      headers: { Accept: 'text/markdown, application/json' },
    })
    return { blob: response.data, fileName: fileNameOf(String(response.headers['content-disposition'] ?? '')) }
  } catch (error) {
    if (isAxiosError(error) && error.response?.data instanceof Blob) {
      try {
        error.response.data = JSON.parse(await error.response.data.text())
      } catch {
        // Không phải JSON: để nguyên, toApiError trả INTERNAL_ERROR.
      }
    }
    throw error
  }
}

/** Tên tệp trong Content-Disposition (ưu tiên filename* mã UTF-8). */
export function fileNameOf(header: string) {
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim())
    } catch {
      // Rơi xuống tên thường.
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain ? plain[1].trim() : 'cau-tra-loi.md'
}
