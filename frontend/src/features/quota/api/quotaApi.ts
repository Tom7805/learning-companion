import { http } from '@/shared/api/httpClient'
import type { AdminQuota, QuotaPolicyInput, QuotaStatus } from '../types'

export const quotaApi = {
  async status() {
    const { data } = await http.get<QuotaStatus>('/quota')
    return data
  },
  async adminOverview() {
    const { data } = await http.get<AdminQuota>('/admin/quotas')
    return data
  },
  async updatePolicy(input: QuotaPolicyInput) {
    const { data } = await http.put<AdminQuota>('/admin/quotas/policy', input)
    return data
  },
  async updateCosts(costs: { task: string; units: number }[]) {
    const { data } = await http.put<AdminQuota>('/admin/quotas/costs', { costs })
    return data
  },
}
