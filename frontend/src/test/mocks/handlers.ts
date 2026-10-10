import { http, HttpResponse } from 'msw'
import type { Account } from '@/features/auth/types'
import type { LegalCurrent } from '@/features/privacy/types'

export const legalCurrent: LegalCurrent = {
  terms: { version: '2026-10-01', summary: 'Tóm tắt điều khoản', effectiveAt: '2026-09-30T17:00:00Z' },
  privacy: { version: '2026-10-01', summary: 'Tóm tắt chính sách', effectiveAt: '2026-09-30T17:00:00Z' },
}

export const verifiedAccount: Account = {
  id: '0b6f8d2e-1111-4f0a-9c1e-000000000001',
  email: 'lan.anh@example.com',
  displayName: 'Lan Anh',
  role: 'LEARNER',
  status: 'ACTIVE',
  emailVerifiedAt: '2026-10-10T06:00:00Z',
}

export function apiError(status: number, code: string, field?: string) {
  return HttpResponse.json(
    {
      code,
      message: `server:${code}`,
      status,
      fieldErrors: field ? [{ field, code, message: `server:${code}` }] : [],
      requestId: 'req-test-1',
    },
    { status },
  )
}

/** Phản hồi mặc định của API; từng bài kiểm thử ghi đè bằng server.use(...). */
export const handlers = [
  http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: false })),
  http.get('*/api/v1/legal/current', () => HttpResponse.json(legalCurrent)),
  http.post('*/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as { email: string }
    return HttpResponse.json({ email: body.email.trim().toLowerCase(), resendAvailableInSeconds: 60 }, { status: 202 })
  }),
  http.post('*/api/v1/auth/verify-email', () => HttpResponse.json(verifiedAccount)),
  http.post('*/api/v1/auth/verify-email/resend', () =>
    HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 }),
  ),
]
