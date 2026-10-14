import { act } from '@testing-library/react'
import { vi } from 'vitest'

/**
 * jsdom không có EventSource. Bản giả này ghi lại các kênh giao diện mở và cho bài kiểm thử phát sự kiện như máy chủ
 * (kênh thông báo trực tiếp, NCL-02-CN-002). Cài bằng {@link installEventSource} trong bài cần dùng.
 */
export class FakeEventSource extends EventTarget {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []

  readonly url: string
  readonly withCredentials: boolean
  readyState = FakeEventSource.CONNECTING
  onopen: ((event: Event) => void) | null = null
  onerror: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null

  constructor(url: string, init?: EventSourceInit) {
    super()
    this.url = url
    this.withCredentials = init?.withCredentials ?? false
    FakeEventSource.instances.push(this)
  }

  close() {
    this.readyState = FakeEventSource.CLOSED
  }

  /** Máy chủ nhận kết nối. */
  open() {
    act(() => {
      this.readyState = FakeEventSource.OPEN
      this.onopen?.(new Event('open'))
    })
  }

  /** Máy chủ gửi một sự kiện có tên kèm dữ liệu JSON. */
  emit(name: string, data: unknown) {
    act(() => {
      this.dispatchEvent(new MessageEvent(name, { data: JSON.stringify(data) }))
    })
  }

  /** Kênh đứt hẳn (ví dụ phiên đã hết): trình duyệt không nối lại. */
  fail() {
    act(() => {
      this.readyState = FakeEventSource.CLOSED
      this.onerror?.(new Event('error'))
    })
  }

  static latest() {
    return FakeEventSource.instances.at(-1)
  }
}

export function installEventSource() {
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
}
