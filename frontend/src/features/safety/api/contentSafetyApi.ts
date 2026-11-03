import { streamTextEvents, type StreamHandlers } from '@/features/admin/lib/aiTestCall'
import { http } from '@/shared/api/httpClient'
import type { ContentSafetyOverview, SafetyLevel, SafetyTrialDone } from '../types'

export const contentSafetyApi = {
  async overview() {
    const { data } = await http.get<ContentSafetyOverview>('/admin/content-safety')
    return data
  },
  async updateLevels(levels: { category: string; level: SafetyLevel }[]) {
    const { data } = await http.put<ContentSafetyOverview>('/admin/content-safety/levels', { levels })
    return data
  },
  /** Chạy thử một câu hỏi (kèm đoạn nguồn mẫu) qua đúng lối của trợ lý hỏi đáp; trả theo luồng sự kiện. */
  trial(
    input: { question: string; source?: string },
    handlers: StreamHandlers<SafetyTrialDone>,
    signal?: AbortSignal,
  ) {
    return streamTextEvents('/api/v1/admin/content-safety/trial', input, handlers, signal)
  },
}
