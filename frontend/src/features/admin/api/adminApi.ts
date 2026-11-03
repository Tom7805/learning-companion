import { http } from '@/shared/api/httpClient'
import type { AdminIdentity, AuditLogPage } from '../types'

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
  async auditLogs(search: AuditLogSearch, before?: string) {
    const { data } = await http.get<AuditLogPage>('/admin/audit-logs', {
      params: { ...search, ...(before ? { before } : {}) },
    })
    return data
  },
}
