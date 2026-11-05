import { describe, expect, it } from 'vitest'
import type { ApiError } from '@/shared/api/errors'
import { normalizeUrl, webImportProblem } from './webSource'

const error = (code: string, details?: Record<string, unknown>): ApiError => ({ code, message: '', status: 422, fieldErrors: [], details })

describe('normalizeUrl', () => {
  it('nhận http/https, tự thêm https:// khi thiếu giao thức', () => {
    expect(normalizeUrl('  https://vi.wikipedia.org/wiki/A ')).toBe('https://vi.wikipedia.org/wiki/A')
    expect(normalizeUrl('example.com/bai')).toBe('https://example.com/bai')
  })

  it('từ chối giao thức khác và chuỗi không phải đường dẫn', () => {
    expect(normalizeUrl('')).toBeNull()
    expect(normalizeUrl('ftp://example.com')).toBeNull()
    expect(normalizeUrl('javascript:alert(1)')).toBeNull()
    expect(normalizeUrl('file:///etc/passwd')).toBeNull()
    expect(normalizeUrl('khong phai duong dan')).toBeNull()
  })
})

describe('webImportProblem', () => {
  it('phân loại lỗi máy chủ', () => {
    expect(webImportProblem(error('WEB_ADDRESS_BLOCKED'))).toEqual({ kind: 'blocked' })
    expect(webImportProblem(error('WEB_ADDRESS_INVALID'))).toEqual({ kind: 'invalid' })
    expect(webImportProblem(error('WEB_PAGE_UNAVAILABLE', { reason: 'LOGIN_REQUIRED' }))).toEqual({
      kind: 'unavailable',
      reason: 'LOGIN_REQUIRED',
    })
    expect(webImportProblem(error('WEB_PAGE_UNAVAILABLE', { reason: 'LẠ' }))).toEqual({ kind: 'unavailable', reason: 'UNREACHABLE' })
    expect(webImportProblem(error('STORAGE_QUOTA_EXCEEDED'))).toEqual({ kind: 'other', code: 'STORAGE_QUOTA_EXCEEDED' })
  })
})
