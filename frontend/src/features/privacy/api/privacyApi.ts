import { http } from '@/shared/api/httpClient'
import type { ConsentHistoryEntry, LegalCurrent, OptionalPurpose, PrivacyStatus } from '../types'

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
  /** Chỉ được gọi khi người học đang bật thống kê ẩn danh; máy chủ cũng kiểm lại đồng ý. */
  async trackPageView(route: string) {
    await http.post('/analytics/events', { event: 'page_view', route })
  },
}
