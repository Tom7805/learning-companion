import type { AiFeature } from '../types'
import { streamTextEvents, type StreamHandlers } from './aiTestCall'

/** Kết quả cuối của một lần thử phiên bản cấu hình. */
export interface PromptTrialDone {
  version: number
  /** Trống khi câu hỏi bị bộ lọc an toàn chặn: mô hình không được gọi (NCL-04-CN-004). */
  providerCode?: string
  inputUnits?: number
  outputUnits?: number
  /** Số các nguồn mẫu bị gắn cờ nghi chèn chỉ dẫn. */
  flaggedSources?: number[]
}

/**
 * Thử nhanh một phiên bản cấu hình (NCL-04-CN-002): câu hỏi mẫu cùng bộ nguồn mẫu đi qua lớp kết nối với đúng nhà cung
 * cấp, mô hình, nhiệt độ và lời nhắc của phiên bản đó. Chữ trả về hiện dần theo luồng.
 */
export function streamPromptTrial(
  feature: AiFeature,
  versionNumber: number,
  input: { question: string; sampleSetId: string },
  handlers: StreamHandlers<PromptTrialDone>,
  signal?: AbortSignal,
) {
  return streamTextEvents(
    `/api/v1/admin/ai/configs/${feature}/versions/${versionNumber}/trial`,
    input,
    handlers,
    signal,
  )
}

/** Chia lời nhắc thành các đoạn thường và đoạn chứa dữ liệu cá nhân để tô đánh dấu, theo vị trí máy chủ trả về. */
export function splitByFindings(text: string, findings: { kind: string; start: number; end: number }[]) {
  const parts: { text: string; kind?: string }[] = []
  let cursor = 0
  for (const finding of [...findings].sort((a, b) => a.start - b.start)) {
    const start = Math.max(cursor, Math.min(finding.start, text.length))
    const end = Math.max(start, Math.min(finding.end, text.length))
    if (start > cursor) parts.push({ text: text.slice(cursor, start) })
    if (end > start) parts.push({ text: text.slice(start, end), kind: finding.kind })
    cursor = end
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor) })
  return parts
}
