import { http as mock, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { http } from './httpClient'

describe('httpClient · mã chống giả mạo', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=ma-moi; path=/'
  })
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('mã trong tiêu đề lệch cookie (hai yêu cầu song song nhận mã mới) thì gửi lại đúng một lần với mã trong cookie', async () => {
    const headers: (string | null)[] = []
    server.use(
      mock.post('*/api/v1/auth/verify-email', ({ request }) => {
        headers.push(request.headers.get('X-XSRF-TOKEN'))
        return headers.length === 1
          ? apiError(403, 'CSRF_TOKEN_INVALID')
          : HttpResponse.json({ ok: true })
      }),
    )

    const response = await http.post('/auth/verify-email', { token: 'abc' })

    expect(response.data).toEqual({ ok: true })
    expect(headers).toEqual(['ma-moi', 'ma-moi'])
  })

  it('vẫn bị từ chối sau lần gửi lại thì báo lỗi, không lặp mãi', async () => {
    let attempts = 0
    server.use(
      mock.post('*/api/v1/auth/verify-email', () => {
        attempts += 1
        return apiError(403, 'CSRF_TOKEN_INVALID')
      }),
    )

    await expect(http.post('/auth/verify-email', { token: 'abc' })).rejects.toMatchObject({ response: { status: 403 } })
    expect(attempts).toBe(2)
  })

  it('không có quyền thật sự (403 khác) thì không gửi lại', async () => {
    let attempts = 0
    server.use(
      mock.post('*/api/v1/exports', () => {
        attempts += 1
        return apiError(403, 'FORBIDDEN')
      }),
    )

    await expect(http.post('/exports')).rejects.toMatchObject({ response: { status: 403 } })
    expect(attempts).toBe(1)
  })
})
