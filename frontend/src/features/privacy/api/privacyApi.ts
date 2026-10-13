import { http } from '@/shared/api/httpClient'
import type {
  AccountDeletionStatus,
  ConsentHistoryEntry,
  DataExport,
  DataExportLink,
  DataExportOverview,
  LegalCurrent,
  OptionalPurpose,
  PrivacyStatus,
} from '../types'

const account = (id: string) => `/accounts/${encodeURIComponent(id)}`

export const privacyApi = {
  async legalCurrent() {
    const { data } = await http.get<LegalCurrent>('/legal/current')
    return data
  },
  async status(accountId: string) {
    const { data } = await http.get<PrivacyStatus>(`${account(accountId)}/privacy`)
    return data
  },
  async setConsent(accountId: string, purpose: OptionalPurpose, granted: boolean) {
    const { data } = await http.put<PrivacyStatus>(`${account(accountId)}/consents/${purpose}`, { granted })
    return data
  },
  async history(accountId: string) {
    const { data } = await http.get<ConsentHistoryEntry[]>(`${account(accountId)}/consents/history`)
    return data
  },
  async acceptTerms(accountId: string, termsVersion: string, privacyVersion: string) {
    const { data } = await http.post<PrivacyStatus>(`${account(accountId)}/terms/accept`, {
      termsVersion,
      privacyVersion,
    })
    return data
  },
  async exportOverview() {
    const { data } = await http.get<DataExportOverview>('/exports')
    return data
  },
  async requestExport() {
    const { data } = await http.post<DataExportOverview>('/exports')
    return data
  },
  async dataExport(id: string) {
    const { data } = await http.get<DataExport>(`/exports/${encodeURIComponent(id)}`)
    return data
  },
  /** Xác nhận lại danh tính rồi nhận đường dẫn tải sống năm phút. */
  async downloadExport(id: string, password?: string) {
    const { data } = await http.post<DataExportLink>(`/exports/${encodeURIComponent(id)}/download`, {
      password: password || undefined,
    })
    return data
  },
  /** Bắt đầu thời gian chờ xóa; mọi phiên, kể cả phiên này, bị đăng xuất ngay khi thành công. */
  async requestDeletion(accountId: string, password?: string) {
    const { data } = await http.post<AccountDeletionStatus>(`${account(accountId)}/deletion`, {
      password: password || undefined,
    })
    return data
  },
  async cancelDeletion(accountId: string) {
    const { data } = await http.delete<AccountDeletionStatus>(`${account(accountId)}/deletion`)
    return data
  },
  /** Chỉ được gọi khi người học đang bật thống kê ẩn danh; máy chủ cũng kiểm lại đồng ý. */
  async trackPageView(route: string) {
    await http.post('/analytics/events', { event: 'page_view', route })
  },
}
