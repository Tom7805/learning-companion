import { http } from '@/shared/api/httpClient'
import type {
  AdminIdentity,
  AiCapability,
  AiGatewayOverview,
  AiProvider,
  AiProviderInput,
  AiRoute,
  AiRouteInput,
  AiUsage,
  AuditLogPage,
  TotpSetup,
  TwoFactorChallenge,
  TwoFactorResult,
} from '../types'

/** Điều kiện tra cứu gửi lên máy chủ: thời gian đã đổi sang mốc ISO (from gồm, to không gồm). */
export interface AuditLogSearch {
  account?: string
  category?: string
  action?: string
  objectType?: string
  from?: string
  to?: string
}

export const adminApi = {
  async me() {
    const { data } = await http.get<AdminIdentity>('/admin/me')
    return data
  },
  /** Bước mã xác thực đang chờ của trình duyệt này (cookie HttpOnly từ bước mật khẩu). */
  async twoFactorChallenge() {
    const { data } = await http.get<TwoFactorChallenge>('/auth/admin/two-factor')
    return data
  },
  async startTotpEnrollment() {
    const { data } = await http.post<TotpSetup>('/auth/admin/two-factor/enrollment')
    return data
  },
  async confirmTotpEnrollment(code: string) {
    const { data } = await http.post<TwoFactorResult>('/auth/admin/two-factor/enrollment/confirm', { code })
    return data
  },
  async verifyTotp(code: string) {
    const { data } = await http.post<TwoFactorResult>('/auth/admin/two-factor/verify', { code })
    return data
  },
  async useRecoveryCode(recoveryCode: string) {
    const { data } = await http.post<TwoFactorResult>('/auth/admin/two-factor/recover', { recoveryCode })
    return data
  },
  async auditLogs(search: AuditLogSearch, before?: string) {
    const { data } = await http.get<AuditLogPage>('/admin/audit-logs', {
      params: { ...search, ...(before ? { before } : {}) },
    })
    return data
  },
  async aiGateway() {
    const { data } = await http.get<AiGatewayOverview>('/admin/ai/gateway')
    return data
  },
  async aiUsage(days: number) {
    const { data } = await http.get<AiUsage>('/admin/ai/usage', { params: { days } })
    return data
  },
  async createAiProvider(input: AiProviderInput) {
    const { data } = await http.post<AiProvider>('/admin/ai/providers', input)
    return data
  },
  async updateAiProvider(id: string, input: AiProviderInput) {
    const { data } = await http.put<AiProvider>(`/admin/ai/providers/${encodeURIComponent(id)}`, input)
    return data
  },
  async setAiProviderEnabled(id: string, enabled: boolean) {
    const { data } = await http.post<AiProvider>(
      `/admin/ai/providers/${encodeURIComponent(id)}/${enabled ? 'enable' : 'disable'}`,
    )
    return data
  },
  async updateAiRoute(capability: AiCapability, input: AiRouteInput) {
    const { data } = await http.put<AiRoute>(`/admin/ai/routes/${capability}`, input)
    return data
  },
  async switchAiFallback(capability: AiCapability) {
    const { data } = await http.post<AiRoute>(`/admin/ai/routes/${capability}/switch`)
    return data
  },
  async acknowledgeAiAlert(id: string) {
    await http.post(`/admin/ai/alerts/${encodeURIComponent(id)}/acknowledge`)
  },
}
