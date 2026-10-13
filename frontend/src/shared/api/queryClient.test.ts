import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { shouldRetry } from './queryClient'

function httpError(status: number) {
  const response = {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { code: status === 404 ? 'NOT_FOUND' : 'INTERNAL_ERROR', message: '', status },
  } as AxiosResponse
  return new AxiosError('failed', String(status), undefined, undefined, response)
}

describe('shouldRetry', () => {
  it('không thử lại câu trả lời dứt khoát 4xx: không tìm thấy, không có quyền, chưa đăng nhập', () => {
    for (const status of [400, 401, 403, 404, 409, 410, 429]) {
      expect(shouldRetry(0, httpError(status))).toBe(false)
    }
  })

  it('thử lại một lần khi lỗi mạng hoặc lỗi máy chủ', () => {
    expect(shouldRetry(0, new AxiosError('Network Error', 'ERR_NETWORK'))).toBe(true)
    expect(shouldRetry(0, httpError(503))).toBe(true)
    expect(shouldRetry(1, httpError(503))).toBe(false)
  })
})
