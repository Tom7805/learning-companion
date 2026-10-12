import { http, HttpResponse } from 'msw'
import type { Account, DeviceSession } from '@/features/auth/types'
import type { LearningDocument } from '@/features/library/types'
import type { Profile } from '@/features/settings/types'
import type { ConsentHistoryEntry, LegalCurrent, PrivacyStatus } from '@/features/privacy/types'

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

export function apiError(status: number, code: string, field?: string, details?: Record<string, unknown>) {
  return HttpResponse.json(
    {
      code,
      message: `server:${code}`,
      status,
      fieldErrors: field ? [{ field, code, message: `server:${code}` }] : [],
      details,
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
  http.post('*/api/v1/auth/login', () => HttpResponse.json(verifiedAccount)),
  http.get('*/api/v1/auth/sessions', () => HttpResponse.json(deviceSessions)),
  http.delete('*/api/v1/auth/sessions/:id', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/sessions/revoke-others', () => HttpResponse.json({ revokedCount: 1 })),
  http.post('*/api/v1/auth/password/forgot', () => HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })),
  http.post('*/api/v1/auth/password/reset/check', () => new HttpResponse(null, { status: 204 })),
  http.post('*/api/v1/auth/password/reset', () => HttpResponse.json({ email: verifiedAccount.email })),
  http.post('*/api/v1/auth/password/change', () => HttpResponse.json({ revokedSessions: 1 })),
  http.get('*/api/v1/library/documents', () =>
    HttpResponse.json([
      {
        id: sampleDocument.id,
        title: sampleDocument.title,
        type: sampleDocument.type,
        originalFilename: sampleDocument.originalFilename,
        sizeBytes: sampleDocument.sizeBytes,
        createdAt: sampleDocument.createdAt,
      },
    ]),
  ),
  http.get('*/api/v1/library/documents/:id', ({ params }) =>
    params.id === sampleDocument.id ? HttpResponse.json(sampleDocument) : apiError(404, 'NOT_FOUND'),
  ),
  http.post('*/api/v1/library/documents/:id/file-link', () =>
    HttpResponse.json({ url: '/api/v1/files/link-1', expiresAt: new Date(Date.now() + 300_000).toISOString() }),
  ),
  http.get('*/api/v1/profiles/me', () => HttpResponse.json(sampleProfile)),
  // Mặc định không bật nhà cung cấp định danh; bài kiểm thử cần thì ghi đè.
  http.get('*/api/v1/auth/oauth/providers', () => HttpResponse.json([])),
  http.get('*/api/v1/accounts/:id/linked-identities', () => HttpResponse.json([])),
  http.post('*/api/v1/auth/password/set', () => new HttpResponse(null, { status: 204 })),
  http.get('*/api/v1/accounts/:id/privacy', () => HttpResponse.json(privacyStatus)),
  http.put('*/api/v1/accounts/:id/consents/:purpose', async ({ params, request }) => {
    const { granted } = (await request.json()) as { granted: boolean }
    return HttpResponse.json({
      ...privacyStatus,
      purposes: privacyStatus.purposes.map((item) =>
        item.purpose === params.purpose
          ? { ...item, granted, documentVersion: '2026-10-01', updatedAt: '2026-10-12T03:20:00Z' }
          : item,
      ),
    })
  }),
  http.get('*/api/v1/accounts/:id/consents/history', () => HttpResponse.json(consentHistory)),
  http.post('*/api/v1/accounts/:id/terms/accept', () =>
    HttpResponse.json({ ...privacyStatus, acceptanceRequired: false }),
  ),
  http.post('*/api/v1/analytics/events', () => HttpResponse.json({ recorded: true }, { status: 202 })),
  http.patch('*/api/v1/profiles/:id', async ({ request }) =>
    HttpResponse.json({ ...sampleProfile, ...((await request.json()) as Partial<Profile>) }),
  ),
  http.put('*/api/v1/profiles/:id/avatar', () =>
    HttpResponse.json({ ...sampleProfile, avatarUrl: `/api/v1/profiles/${sampleProfile.id}/avatar?v=1` }),
  ),
  http.delete('*/api/v1/profiles/:id/avatar', () => HttpResponse.json({ ...sampleProfile, avatarUrl: null })),
  http.get('*/api/v1/files/:token', () =>
    new HttpResponse(sampleDocument.contentText, { headers: { 'Content-Type': 'text/plain; charset=utf-8' } }),
  ),
]

export const sampleDocument: LearningDocument = {
  id: '5d1c0e6a-2222-4b1a-8c3e-000000000001',
  title: 'Ghi chú: Định luật Ohm',
  type: 'TEXT',
  contentText: 'Định luật Ohm\n\nI = U / R',
  originalFilename: 'dinh-luat-ohm.txt',
  mimeType: 'text/plain; charset=utf-8',
  sizeBytes: 1536,
  createdAt: '2026-10-11T03:00:00Z',
  updatedAt: '2026-10-11T03:00:00Z',
}

export const deviceSessions: DeviceSession[] = [
  {
    id: 'phien-thu-vien',
    browser: 'Chrome',
    operatingSystem: 'Windows',
    deviceType: 'DESKTOP',
    localNetwork: false,
    location: 'Hà Nội, VN',
    signedInAt: '2026-10-10T01:00:00Z',
    lastActiveAt: '2026-10-10T02:00:00Z',
    expiresAt: '2026-10-10T04:00:00Z',
    rememberDevice: false,
    current: false,
  },
  {
    id: 'phien-dien-thoai',
    browser: 'Chrome',
    operatingSystem: 'Android',
    deviceType: 'MOBILE',
    localNetwork: true,
    signedInAt: '2026-10-09T01:00:00Z',
    lastActiveAt: '2026-10-10T03:00:00Z',
    expiresAt: '2026-11-08T01:00:00Z',
    rememberDevice: true,
    current: true,
  },
]

export const sampleProfile: Profile = {
  id: '7a3e9c10-3333-4c2b-9d4f-000000000001',
  displayName: 'Lan Anh',
  email: 'lan.anh@example.com',
  timeZone: null,
  assistantLanguage: 'vi',
  dailyGoalMinutes: 15,
  dayRolloverHour: 4,
  avatarUrl: null,
  updatedAt: '2026-10-12T03:00:00Z',
}

export const privacyStatus: PrivacyStatus = {
  accountId: verifiedAccount.id,
  terms: { ...legalCurrent.terms, highlights: ['Bạn dùng ứng dụng cho việc học của chính mình.'], changes: [] },
  privacy: { ...legalCurrent.privacy, highlights: ['Dữ liệu học tập thuộc về bạn.'], changes: [] },
  acceptedTermsVersion: '2026-10-01',
  acceptedPrivacyVersion: '2026-10-01',
  acceptanceRequired: false,
  purposes: [
    { purpose: 'ANONYMOUS_USAGE_ANALYTICS', granted: false, documentVersion: null, updatedAt: null },
    { purpose: 'PRODUCT_NEWS_EMAIL', granted: false, documentVersion: null, updatedAt: null },
  ],
}

export const consentHistory: ConsentHistoryEntry[] = [
  { id: 'h2', purpose: 'PRIVACY_POLICY', granted: true, documentVersion: '2026-10-01', recordedAt: '2026-10-10T06:00:00Z' },
  { id: 'h1', purpose: 'TERMS_OF_SERVICE', granted: true, documentVersion: '2026-10-01', recordedAt: '2026-10-10T06:00:00Z' },
]
