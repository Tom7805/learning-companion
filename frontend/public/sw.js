// Service worker của Learning Companion (NCL-02-CN-001): cài được như ứng dụng và mở lại nhanh kể cả khi mạng chập chờn.
//
// - Khi cài: lưu đệm phần khung (trang gốc, mã và kiểu đã băm tên, phông chữ, biểu tượng). Danh sách tệp và phiên bản
//   do vite.config.ts điền vào lúc dựng bản thật; máy phát triển không đăng ký service worker này.
// - Mở trang: hỏi mạng trước để luôn có bản mới nhất. Mạng chậm quá NAVIGATION_TIMEOUT_MS hoặc mất mạng thì trả khung
//   đã lưu, ứng dụng tự hiện khung xương kèm thông báo đang kết nối lại.
// - Tệp đã băm tên trong /assets/ không bao giờ đổi nội dung nên lấy từ bộ đệm trước.
// - Không bao giờ lưu đệm /api: dữ liệu học tập và phiên đăng nhập luôn đi thẳng tới máy chủ.

const BUILD = 'dev'
const PRECACHE = []
const CACHE_PREFIX = 'lc-shell-'
const CACHE = CACHE_PREFIX + BUILD
const SHELL_URL = '/'
const NAVIGATION_TIMEOUT_MS = 3000

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll([SHELL_URL, ...PRECACHE].map((url) => new Request(url, { cache: 'reload' }))))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE).map((key) => caches.delete(key))),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return

  if (request.mode === 'navigate') {
    // Mọi đường dẫn của ứng dụng trả cùng một trang gốc, nên trang mới nhất từ mạng được lưu làm khung.
    const network = fetch(request)
    event.waitUntil(store(network, SHELL_URL, isHtml))
    event.respondWith(networkFirst(network))
  } else if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request))
  } else if (PRECACHE.includes(url.pathname)) {
    const network = fetch(request)
    event.waitUntil(store(network, request))
    event.respondWith(staleWhileRevalidate(request, network))
  }
})

/** Lưu bản sao câu trả lời thành công; mất mạng thì bỏ qua, bộ đệm giữ bản cũ. */
function store(network, key, accept = () => true) {
  return network
    .then((response) => {
      if (!response.ok || response.type !== 'basic' || !accept(response)) return undefined
      const copy = response.clone()
      return caches.open(CACHE).then((cache) => cache.put(key, copy))
    })
    .catch(() => undefined)
}

async function networkFirst(network) {
  const cached = await caches.match(SHELL_URL, { cacheName: CACHE })
  if (!cached) return network
  const fresh = network.then(
    (response) => (response.ok ? response : cached),
    () => cached,
  )
  const slow = new Promise((resolve) => setTimeout(() => resolve(cached), NAVIGATION_TIMEOUT_MS))
  return Promise.race([fresh, slow])
}

async function cacheFirst(request) {
  const cache = await caches.open(CACHE)
  const cached = await cache.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok && response.type === 'basic') await cache.put(request, response.clone())
  return response
}

async function staleWhileRevalidate(request, network) {
  const cached = await caches.match(request, { cacheName: CACHE })
  return cached ?? network
}

function isHtml(response) {
  return (response.headers.get('content-type') || '').includes('text/html')
}
