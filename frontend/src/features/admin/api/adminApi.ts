import { http } from '@/shared/api/httpClient'
import type {
  AdminIdentity,
  AiCapability,
  AiConfigDiff,
  AiConfigOverview,
  AiConfigVersion,
  AiConfigVersionInput,
  AiFeature,
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
  async aiConfigs() {
    const { data } = await http.get<AiConfigOverview>('/admin/ai/configs')
    return data
  },
  async aiConfigVersions(feature: AiFeature) {
    const { data } = await http.get<AiConfigVersion[]>(`/admin/ai/configs/${feature}/versions`)
    return data
  },
  async aiConfigDiff(feature: AiFeature, from: number, to: number) {
    const { data } = await http.get<AiConfigDiff>(`/admin/ai/configs/${feature}/diff`, { params: { from, to } })
    return data
  },
  /** Mỗi lần lưu tạo một phiên bản nháp mới; phiên bản đã có không bao giờ bị sửa. */
  async saveAiConfig(feature: AiFeature, input: AiConfigVersionInput) {
    const { data } = await http.post<AiConfigVersion>(`/admin/ai/configs/${feature}/versions`, input)
    return data
  },
  async publishAiConfig(feature: AiFeature, versionNumber: number) {
    const { data } = await http.post<AiConfigVersion>(
      `/admin/ai/configs/${feature}/versions/${versionNumber}/publish`,
    )
    return data
  },
  /** Thu hồi phiên bản đang chạy và phát hành lại phiên bản phát hành trước đó. */
  async rollbackAiConfig(feature: AiFeature) {
    const { data } = await http.post<AiConfigVersion>(`/admin/ai/configs/${feature}/rollback`)
    return data
  },
}
