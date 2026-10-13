/**
 * jsdom không có window.matchMedia. Bản giả này tính các truy vấn ứng dụng dùng (độ rộng màn hình, chế độ tối của hệ
 * điều hành, chạy như ứng dụng đã cài) từ một trạng thái đổi được trong bài kiểm thử, và báo "change" như trình duyệt.
 */
interface MediaState {
  width: number
  dark: boolean
  standalone: boolean
}

const DEFAULT_MEDIA: MediaState = { width: 1280, dark: false, standalone: false }

let media: MediaState = { ...DEFAULT_MEDIA }
const lists = new Set<FakeMediaQueryList>()

function evaluate(query: string) {
  return query.split(',').some((part) =>
    part.split(/\band\b/).every((condition) => {
      const min = /min-width:\s*([\d.]+)px/.exec(condition)
      if (min) return media.width >= Number(min[1])
      const max = /max-width:\s*([\d.]+)px/.exec(condition)
      if (max) return media.width <= Number(max[1])
      if (/prefers-color-scheme:\s*dark/.test(condition)) return media.dark
      if (/prefers-color-scheme:\s*light/.test(condition)) return !media.dark
      if (/display-mode:\s*standalone/.test(condition)) return media.standalone
      return false
    }),
  )
}

class FakeMediaQueryList extends EventTarget {
  readonly media: string
  onchange: ((event: Event) => void) | null = null
  private last: boolean

  constructor(query: string) {
    super()
    this.media = query
    this.last = evaluate(query)
  }

  get matches() {
    return evaluate(this.media)
  }

  addListener(listener: () => void) {
    this.addEventListener('change', listener)
  }

  removeListener(listener: () => void) {
    this.removeEventListener('change', listener)
  }

  notifyIfChanged() {
    const now = this.matches
    if (now === this.last) return
    this.last = now
    const event = Object.assign(new Event('change'), { matches: now, media: this.media })
    this.onchange?.(event)
    this.dispatchEvent(event)
  }
}

export function installMatchMedia() {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string) => {
      const list = new FakeMediaQueryList(query)
      lists.add(list)
      return list
    },
  })
}

/** Đổi màn hình hoặc cài đặt hệ điều hành giữa chừng; mọi nơi đang nghe đều nhận "change". */
export function setMedia(next: Partial<MediaState>) {
  media = { ...media, ...next }
  lists.forEach((list) => list.notifyIfChanged())
}

export function resetMedia() {
  media = { ...DEFAULT_MEDIA }
  lists.clear()
}
