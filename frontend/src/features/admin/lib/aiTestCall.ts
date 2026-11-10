import type { SafetyOutcome } from '@/features/safety'
import { postEventStream } from '@/shared/api/sse'

/**
 * Kết quả cuối của lời gọi thử: nhà cung cấp đã trả lời và số đơn vị được đo. Câu hỏi bị bộ lọc an toàn chặn thì mô
 * hình không được gọi nên không có nhà cung cấp.
 */
export interface AiTestCallDone {
  status?: 'ANSWERED' | 'BLOCKED' | 'SUPPORT'
  flaggedSources?: number[]
  providerCode?: string
  inputUnits?: number
  outputUnits?: number
}

export interface StreamHandlers<TDone> {
  onChunk: (text: string) => void
  onDone: (done: TDone) => void
  onError: (code: string) => void
  /** Bộ lọc an toàn chặn câu hỏi, thay nội dung sinh ra hoặc trả lời ân cần (NCL-04-CN-004). */
  onSafety?: (outcome: SafetyOutcome) => void
}

export type AiTestCallHandlers = StreamHandlers<AiTestCallDone>

export { parseEvents } from '@/shared/api/sse'

/**
 * Gửi một yêu cầu POST trả về luồng sự kiện "chunk" (có thể kèm "safety"), rồi "done" hoặc "error", và đưa từng đoạn
 * chữ cho giao diện ngay khi tới.
 */
export async function streamTextEvents<TDone>(
  url: string,
  body: unknown,
  handlers: StreamHandlers<TDone>,
  signal?: AbortSignal,
) {
  await postEventStream(
    url,
    body,
    (name, data) => {
      if (name === 'chunk') handlers.onChunk(String(data.text ?? ''))
      else if (name === 'safety') handlers.onSafety?.(data as unknown as SafetyOutcome)
      else if (name === 'done') {
        handlers.onDone(data as unknown as TDone)
        return true
      } else if (name === 'error') {
        handlers.onError(String(data.code ?? 'INTERNAL_ERROR'))
        return true
      }
      return false
    },
    (code) => handlers.onError(code),
    signal,
  )
}

/** Lời gọi thử sinh văn bản qua lớp kết nối (NCL-04-CN-001). */
export function streamAiTestCall(prompt: string, handlers: AiTestCallHandlers, signal?: AbortSignal) {
  return streamTextEvents('/api/v1/admin/ai/test-call', { prompt }, handlers, signal)
}
