import type { ChatMessage } from '../types'

const GENERATED = new Set(['STREAMING', 'COMPLETE', 'STOPPED', 'FAILED'])

/**
 * Câu trả lời có nội dung do mô hình sinh ra, cần nhãn "Do AI sinh" (NCL-04-CN-005). Hội thoại đọc lại từ máy chủ đã có
 * {@code aiGenerated}; câu trả lời đang hiện trong phiên thì suy từ trạng thái: đang hiện, xong, dừng hay lỗi giữa
 * chừng mà đã có chữ. Lời nhắc của hệ thống (chặn an toàn, không tìm thấy, chưa chọn nguồn) không gắn nhãn.
 */
export function isAiGenerated(message: ChatMessage) {
  if (message.role !== 'ASSISTANT') return false
  if (message.aiGenerated !== undefined) return message.aiGenerated
  return message.content.trim() !== '' && GENERATED.has(message.status)
}
