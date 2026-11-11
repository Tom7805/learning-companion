/**
 * Luồng sự kiện máy chủ trả về cho một yêu cầu POST (POST nên không dùng được EventSource): đọc thân phản hồi theo luồng
 * và đưa từng sự kiện (tên, dữ liệu JSON) cho nơi gọi ngay khi tới. Dùng chung cho mọi chức năng sinh nội dung hiện
 * dần (lời gọi thử của quản trị viên, hỏi đáp của người học).
 */

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
 * Gửi POST và đọc luồng sự kiện. {@code onEvent} trả về true khi đó là sự kiện kết thúc ("done", "error"). Máy chủ từ
 * chối trước khi có luồng (404, 400…) thì {@code onRejected} nhận mã lỗi; luồng đứt giữa chừng thì nhận NETWORK_ERROR.
 * Người gọi hủy bằng {@code signal} thì không báo gì.
 */
export async function postEventStream(
  url: string,
  body: unknown,
  onEvent: (name: string, data: Record<string, unknown>) => boolean,
  onRejected: (code: string, status: number) => void,
  signal?: AbortSignal,
) {
  if (!xsrfToken()) {
    await fetch('/api/v1/auth/session', { credentials: 'include' })
  }
  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(xsrfToken() ? { 'X-XSRF-TOKEN': xsrfToken() as string } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch {
    if (!signal?.aborted) onRejected('NETWORK_ERROR', 0)
    return
  }
  if (!response.ok || !response.body) {
    let code = 'INTERNAL_ERROR'
    try {
      code = ((await response.json()) as { code?: string }).code ?? code
    } catch {
      // Phản hồi không phải JSON: giữ mã lỗi chung.
    }
    onRejected(code, response.status)
    return
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  try {
    while (!finished) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parsed = parseEvents(buffer)
      buffer = parsed.rest
      for (const event of parsed.events) {
        if (onEvent(event.name, JSON.parse(event.data) as Record<string, unknown>)) {
          finished = true
          break
        }
      }
    }
  } catch {
    if (signal?.aborted) return
  }
  if (!finished && !signal?.aborted) onRejected('NETWORK_ERROR', 0)
}
