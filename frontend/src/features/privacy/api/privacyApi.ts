import { http } from '@/shared/api/httpClient'
import type { LegalCurrent } from '../types'

export const privacyApi = {
  async legalCurrent() {
    const { data } = await http.get<LegalCurrent>('/legal/current')
    return data
  },
}
