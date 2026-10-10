import axios from 'axios'

const XSRF_COOKIE = 'XSRF-TOKEN'
const MUTATING = new Set(['post', 'put', 'patch', 'delete'])

/**
 * Phiên đăng nhập nằm trong cookie HttpOnly. Máy chủ phát cookie XSRF-TOKEN, axios đọc và gửi lại
 * qua tiêu đề X-XSRF-TOKEN cho mọi yêu cầu thay đổi dữ liệu.
 */
export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: XSRF_COOKIE,
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

function hasXsrfCookie() {
  return document.cookie.split(';').some((part) => part.trim().startsWith(`${XSRF_COOKIE}=`))
}

// Lần gửi biểu mẫu đầu tiên có thể diễn ra trước khi có cookie CSRF: lấy cookie trước rồi mới gửi.
http.interceptors.request.use(async (config) => {
  if (MUTATING.has((config.method ?? 'get').toLowerCase()) && !hasXsrfCookie()) {
    await axios.get('/api/v1/auth/session', { withCredentials: true })
  }
  return config
})
