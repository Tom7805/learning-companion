import type { SafetyOutcome } from '@/features/safety'

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

function xsrfToken() {
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : undefined
}

/** Tách luồng sự kiện máy chủ thành từng sự kiện (tên, dữ liệu); phần dở dang giữ lại chờ đoạn sau. */
export function parseEvents(buffer: string): { events: { name: string; data: string }[]; rest: string } {
  const blocks = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const rest = blocks.pop() ?? ''
  const events = blocks
    .map((block) => {
      let name = 'message'
      const data: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) name = line.slice(6).trim()
        else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
      }
      return { name, data: data.join('\n') }
    })
    .filter((event) => event.data.length > 0)
  return { events, rest }
}

/**
 * Gửi một yêu cầu POST trả về luồng sự kiện "chunk" (có thể kèm "safety"), rồi "done" hoặc "error" (POST nên không dùng được EventSource):
 * đọc thân phản hồi theo luồng và đưa từng đoạn chữ cho giao diện ngay khi tới.
 */
export async function streamTextEvents<TDone>(
  url: string,
  body: unknown,
  handlers: StreamHandlers<TDone>,
  signal?: AbortSignal,
) {
  if (!xsrfToken()) {
    await fetch('/api/v1/auth/session', { credentials: 'include' })
  }
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(xsrfToken() ? { 'X-XSRF-TOKEN': xsrfToken() as string } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) {
    let code = 'INTERNAL_ERROR'
    try {
      code = ((await response.json()) as { code?: string }).code ?? code
    } catch {
      // Phản hồi không phải JSON: giữ mã lỗi chung.
    }
    handlers.onError(code)
    return
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  while (!finished) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parsed = parseEvents(buffer)
    buffer = parsed.rest
    for (const event of parsed.events) {
      const data = JSON.parse(event.data) as Record<string, unknown>
      if (event.name === 'chunk') handlers.onChunk(String(data.text ?? ''))
      else if (event.name === 'safety') handlers.onSafety?.(data as unknown as SafetyOutcome)
      else if (event.name === 'done') {
        handlers.onDone(data as unknown as TDone)
        finished = true
      } else if (event.name === 'error') {
        handlers.onError(String(data.code ?? 'INTERNAL_ERROR'))
        finished = true
      }
    }
  }
  if (!finished) handlers.onError('NETWORK_ERROR')
}

/** Lời gọi thử sinh văn bản qua lớp kết nối (NCL-04-CN-001). */
export function streamAiTestCall(prompt: string, handlers: AiTestCallHandlers, signal?: AbortSignal) {
  return streamTextEvents('/api/v1/admin/ai/test-call', { prompt }, handlers, signal)
}
