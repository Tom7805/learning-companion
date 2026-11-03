import { http } from '@/shared/api/httpClient'
import type { AdminIdentity, AuditLogPage, TotpSetup, TwoFactorChallenge, TwoFactorResult } from '../types'

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
}
